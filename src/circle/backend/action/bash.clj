(ns circle.backend.action.bash
  "functions for running bash on remote instances, and build actions for same."
  (:require pallet.action-plan)
  (:require [clojure.string :as str])
  (:require fs)
  (:use [circle.util.core :only (apply-map)])
  (:use [circle.model.build :only (log-ns build-log build-log-error checkout-dir)])
  (:use [slingshot.slingshot :only (try+)])
  (:use [circle.util.time :only (period-to-s)])
  (:require [circle.sh :as sh])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.action :as action])
  (:require [clj-time.core :as time]))

(defn emit-bash [command environment pwd]
  (sh/emit-form command
                :environment environment
                :pwd pwd))

(defn remote-bash
  "Execute bash code on the remote server.

   ssh-map is a map containing the keys :username, :public-key, :private-key :ip-addr. All keys are required."
  [ssh-map command & {:keys [environment
                          pwd]
                   :as opts}]
  (let [cmd (emit-bash command environment pwd)
        _ (build-log "running (%s %s %s): %s" command pwd environment cmd)
        result (apply-map ssh/remote-exec ssh-map cmd opts)]
    (build-log "%s returned" (-> result :exit)) ;; only log exit, rest should be handled by ssh
    result))

(defn ensure-ip-addr
  "Make sure the build's node has an ip address."
  [build]
  (if (not (-> @build :node :ip-addr))
    (do
      (assert (-> @build :instance-ids (seq)))
      (let [instance-id (-> @build :instance-ids (first))
            ip-addr (ec2/public-ip instance-id)
            new-node (merge (-> @build :node) {:ip-addr ip-addr})]
      (dosync
       (alter build assoc :node new-node))))
    build))

(defn remote-bash-build
  [build command & {:as opts}]
  (ensure-ip-addr build)
  (apply-map remote-bash (-> @build :node) command opts))

(defn action-name [command]
  (if (coll? command)
    (->> command
         (map #(str/join " " %))
         (str/join "; "))
    (str command)))

(defn bash
  "Returns a new action that executes bash on the host. Command is a
  string. If pwd is not specified, defaults to the root of the build's
  checkout dir"
  [command & {:keys [name abort-on-nonzero environment pwd type relative-timeout absolute-timeout]
           :or {abort-on-nonzero true}
           :as opts}]
  (let [name (or name (action-name command))
        bash-command (emit-bash command environment pwd)
        relative-timeout (or relative-timeout (time/minutes 20))
        absolute-timeout (or absolute-timeout (time/hours 2))]
    (action/action :name name
                   :type type
                   :command (str command)
                   :bash-command bash-command
                   :environment environment
                   :pwd pwd
                   :abort-on-nonzero abort-on-nonzero
                   :relative-timeout (period-to-s relative-timeout)
                   :absolute-timeout (period-to-s absolute-timeout)
                   :act-fn (fn [build]
                             (try+
                              (let [pwd (fs/join (checkout-dir build) pwd)
                                    opts (merge opts {:pwd pwd
                                                      :relative-timeout relative-timeout
                                                      :absolute-timeout absolute-timeout})
                                    result (apply-map remote-bash-build build command opts)]
                                (when (and (not= 0 (-> result :exit)) abort-on-nonzero)
                                   (action/abort! build (str command " returned exit code " (-> result :exit))))
                                 ;; only add exit code, :out and :err are handled by hooking ssh/handle-out and ssh/handle-err in action.clj
                                 (action/add-action-result (select-keys result [:exit])))
                              (catch [:type :circle.backend.ssh/ssh-timeout] {:keys [timeout-type timeout]}
                                ;; (println "caught: " &throw-context)
                                (let [msg (condp = timeout-type
                                            :absolute (format "action %s took more than %s to run" name (period-to-s timeout))
                                            :relative (format "action %s took more than %s since last output" name (period-to-s timeout)))]
                                  (action/abort-timeout! build msg))))))))
