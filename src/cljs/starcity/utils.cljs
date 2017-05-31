(ns starcity.utils
  (:import [goog.ui IdGenerator]))

#_(defn by-class [class-name]
  (array-seq (.getElementsByClassName js/document class-name)))

#_(defn guid []
  (.getNextUniqueId (.getInstance IdGenerator)))

#_(defn remove-at
  "Remove element at index `i` from vector `v`."
  [v i]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn find-by
  [pred coll]
  (first (filter pred coll)))

(defn transform-when-key-exists
  "(transform-when-key-exists
     {:a 1
      :b 2}
     {:a #(inc %)
      :c #(inc %)})

   => {:a 2 :b 2}"
  [source transformations]
  (reduce
   (fn [m x]
     (merge m
            (let [[key value] x
                  t (get transformations key)]
              (if (and (map? value) (map? t))
                (assoc m key (transform-when-key-exists value t))
                (if-let [transform t]
                  {key (transform value)}
                  x)))))
   {}
   source))
