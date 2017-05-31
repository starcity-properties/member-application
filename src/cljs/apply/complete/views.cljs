(ns apply.complete.views)

(defn complete []
  [:section.section
   [:div.container
    [:h1.title.is-1 "Your application is complete!"]
    [:p.subtitle.is-4 "Expect to hear from us shortly via email."]
    [:div.content.is-medium
     [:p "If you'd like to get in touch sooner, please reach out to us "
      [:a {:href "mailto:team@joinstarcity.com"} "here"] "."]]]])
