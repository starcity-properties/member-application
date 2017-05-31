(ns starcity.components.form)

(defn label
  ([content]
   [:label.label content])
  ([content for]
   [:label.label {:for for} content]))
