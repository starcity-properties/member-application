(ns starcity.components.loading)

(defn container [& [text]]
  [:div.container.has-text-centered
   [:h1.is-3.subtitle (or text "Loading...")]
   [:div.sk-double-bounce
    [:div.sk-child.sk-double-bounce1]
    [:div.sk-child.sk-double-bounce2]]])

(defn fill-container [& [text]]
  [:section.hero.is-fullheight
   [:div.hero-body (container text)]])
