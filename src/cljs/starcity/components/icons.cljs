(ns starcity.components.icons)

(def size-classes
  {:large   "is-large"
   :medium  "is-medium"
   :small   "is-small"})

(defn- size-class [size]
  (get size-classes size))

(defn icon
  ([name]
   (icon name :default))
  ([name size]
   [:span.icon (when-let [cls (size-class size)]
                 {:class cls})
    [:i.fa {:class (str "fa-" name)}]]))

;; Icons
(def check (partial icon "check"))
(def check-circle (partial icon "check-circle"))
(def angle-right (partial icon "angle-right"))
(def angle-left (partial icon "angle-left"))
(def phone (partial icon "phone"))
(def user (partial icon "user"))
(def email (partial icon "envelope"))
(def error (partial icon "exclamation-circle"))
(def hourglass (partial icon "hourglass-end"))
(def settings (partial icon "cog"))
(def stripe (partial icon "cc-stripe"))
(def cross (partial icon "times"))
(def cross-circle (partial icon "times-circle"))
(def question-circle (partial icon "question-circle"))
(def question (partial icon "question"))
(def search (partial icon "search"))
(def bank (partial icon "university"))
(def usd (partial icon "usd"))
