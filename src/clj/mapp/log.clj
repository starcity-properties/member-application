(ns mapp.log
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [mount.core :as mount :refer [defstate]]
            [mapp.config :as config :refer [config]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling]
            [taoensso.timbre.appenders.core :as appenders]))

;; =============================================================================
;; Configuration
;; =============================================================================

(defn- appender [appender filename]
  (case appender
    :spit    {:spit (appenders/spit-appender {:fname filename})}
    :rolling {:rolling (rolling/rolling-appender {:path filename})}))

(s/def ::event-vargs
  (s/cat :event keyword?
         :params map?))

(defn- event-vargs
  [data event params]
  (try
    (assoc data :vargs
           [(-> {:event event}
                (merge (when-let [err (:?err data)] {:error-data (or (ex-data err) :none)})
                       params)
                json/generate-string)])
    (catch Throwable t
      (timbre/warn t "Error encountered while attempting to encode vargs.")
      data)))

(defn- wrap-event-format
  "Middleware that transforms the user's log input into a JSON
  string with an `event` key. This is used to make search effective in LogDNA.

  Only applies when timbre is called with input of the form:

  (timbre/info ::event {:map :of-data})"
  [{:keys [vargs] :as data}]
  (if (s/valid? ::event-vargs vargs)
    (let [{:keys [event params]} (s/conform ::event-vargs vargs)]
      (event-vargs data event params))
    data))

(defstate logger
  :start (timbre/merge-config!
          {:level      (config/log-level config)
           :middleware [wrap-event-format]
           :appenders  (appender (config/log-appender config)
                                 (config/log-file config))}))
