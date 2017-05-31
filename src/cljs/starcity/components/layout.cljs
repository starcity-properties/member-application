(ns starcity.components.layout)

(defn hero
  ([title]
   (hero title nil))
  ([title subtitle]
   [:section.hero
    [:div.hero-body
     [:div.container
      [:h1.title title]
      (when subtitle [:h2.subtitle subtitle])]]]))

(defn heading
  ([title]
   (heading title nil))
  ([title subtitle]
   [:section.section
    [:div.container
     [:div.heading
      [:h1.title title]
      (when subtitle [:h2.subtitle subtitle])]]]))
