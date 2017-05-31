(ns apply.api)

(def ^:private base-uri
  "/api")

(defn route [& fragments]
  (->> fragments (interpose "/") (apply str base-uri)))
