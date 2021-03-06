(ns frontend.components.footer)

(defn footer []
  [:nav.footer-nav
   [:ul
    [:li [:a.logo {:href "/"}]]
    [:li [:a {:href "/about"} "About"]]
    [:li [:a {:href "/pricing"} "Pricing"]]
    [:li [:a {:href "/docs"} "Documentation"]]
    [:li [:a {:href "/jobs"} "Jobs"]]
    [:li [:a {:href "http://blog.circleci.com"} "Blog"]]
    [:li [:a {:href "/privacy"} "Privacy"]]
    [:li [:a {:href "/security"} "Security"]]
    [:li [:a {:href "/enterprise"} "Enterprise"]]
    [:li [:a {:href "/changelog"} "Changelog"]]
    [:li [:a {:href "/about#contact"} "Contact"]]
    [:li
     [:a {:href "https://www.hipchat.com/gjwkHcrD5"} "Live Support"]]
    [:li
     [:a.fa.fa-twitter
      {:title "Follow CircleCI on Twitter",
       :href "https://twitter.com/circleci"}]]]])
