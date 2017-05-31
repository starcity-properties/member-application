(ns starcity.dates
  "Light-weight date-time formatting.

  https://dirk.net/2012/07/14/date-formats-in-clojurescript/"
  (:require [goog.i18n.DateTimeFormat :as dtf]))

(def format-map
  (let [f goog.i18n.DateTimeFormat.Format]
    {:full-date (.-FULL_DATE f)
     :full-datetime (.-FULL_DATETIME f)
     :full-time (.-FULL_TIME f)
     :long-date (.-LONG_DATE f)
     :long-datetime (.-LONG_DATETIME f)
     :long-time (.-LONG_TIME f)
     :medium-date (.-MEDIUM_DATE f)
     :medium-datetime (.-MEDIUM_DATETIME f)
     :medium-time (.-MEDIUM_TIME f)
     :short-date (.-SHORT_DATE f)
     :short-datetime (.-SHORT_DATETIME f)
     :short-time (.-SHORT_TIME f)}))

(defn format; -date-generic
  "Format a date using either the built-in goog.i18n.DateTimeFormat.Format enum
  or a formatting string like \"dd MMMM yyyy\""
  [date-format date]
  (.format (goog.i18n.DateTimeFormat.
            (or (date-format format-map) date-format))
           (js/Date. date)))
