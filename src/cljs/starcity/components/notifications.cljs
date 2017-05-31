(ns starcity.components.notifications)

(defn- notification [class content on-delete]
  [:div.notification {:class class}
   [:button.delete {:on-click on-delete}]
   content])

(def danger (partial notification "is-danger"))
(def success (partial notification "is-success"))
