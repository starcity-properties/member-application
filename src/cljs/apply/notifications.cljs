(ns apply.notifications)

(defn- notification [type message]
  {:type type :message message})

(def error (partial notification :error))
(def success (partial notification :success))
