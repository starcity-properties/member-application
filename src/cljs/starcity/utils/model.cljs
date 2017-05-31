(ns starcity.utils.model)

(defn- to-sequential
  [root-key-or-path]
  (if (sequential? root-key-or-path)
    root-key-or-path
    [root-key-or-path]))

(defn assoc-in-db*
  "Creates functions like `clojure.core/assoc-in` that prepend a `root-db-key`
  to the beginning of the path (`ks`)."
  [root-key-or-path]
  (fn [db ks v]
    (assoc-in db (concat (to-sequential root-key-or-path) ks) v)))

(defn get-in-db*
  "Creates functions like `clojure.core/get-in` that prepend s `root-db-key` to
  the beginning of the lookup if `db` has that key -- otherwise behaves exactly
  like `clojure.core/get-in`"
  [root-key-or-path]
  (fn [db ks]
    (if-let [db' (get-in db (to-sequential root-key-or-path))]
      ;; We're searching from the root
      (get-in db' ks)
      ;; We're already under `root-key-or-path`
      (get-in db ks))))
