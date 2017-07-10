(ns user
  (:require [clojure.core.async :as a]
            [clojure.spec.test :as stest]
            [clojure.tools.namespace.repl :refer [refresh]]
            [datomic.api :as d]
            [figwheel-sidecar.repl-api :as ra]
            [mailer.core :as mailer]
            [mapp.config :as config :refer [config]]
            [mapp.datomic :refer [conn]]
            [mapp.log]
            [mapp.server]
            [mount.core :as mount :refer [defstate]]
            [reactor.deps :as deps]
            [reactor.reactor :as reactor]
            [reactor.services.community-safety :as cs]
            [reactor.services.slack :as slack]
            [reactor.services.weebly :as weebly]
            [ribbon.core :as ribbon]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)


;; =============================================================================
;; Reactor
;; =============================================================================


(defstate community-safety
  :start (reify cs/ICommunitySafety
           (background-check [this user-id first-name last-name email dob]
             (cs/background-check this user-id first-name last-name email dob {}))
           (background-check [this user-id first-name last-name email dob opts]
             (let [c (a/chan 1)]
               (a/put! c {:body {} :headers {:location "http://localhost"}})
               c))))


(defstate mailer
  :start (mailer/mailgun (get-in config [:mailgun :api-key])
                         (get-in config [:mailgun :domain])
                         {:sender  (get-in config [:mailgun :sender])
                          :send-to "josh@joinstarcity.com"}))


(defstate slack
  :start (slack/slack (get-in config [:secrets :slack :webhook])
                      (get-in config [:slack :username])
                      "#debug"))


(defstate weebly
  :start (reify weebly/WeeblyPromote
           (subscribe! [this email]
             (let [c (a/chan 1)]
               (a/put! c {:body {:email email}})
               c))))


(defstate stripe
  :start (ribbon/stripe-connection (config/stripe-secret-key config)))


(defstate ^:private tx-report-ch
  :start (a/chan (a/sliding-buffer 256))
  :stop (a/close! tx-report-ch))


(defstate ^:private tx-report-queue
  :start (a/thread
           (try
             (let [queue (d/tx-report-queue conn)]
               (while true
                 (let [report (.take queue)]
                   (a/>!! tx-report-ch report))))
             (catch Exception e
               (timbre/error e "TX-REPORT-TAKE exception")
               (throw e))))
  :stop (d/remove-tx-report-queue conn))


(defstate mult :start (a/mult tx-report-ch))


(defstate reactor
  :start (let [deps (deps/deps community-safety
                               mailer
                               slack
                               weebly
                               stripe
                               (config/root-domain config))]
           (reactor/start! conn mult deps))
  :stop (reactor/stop! mult reactor))


;; =============================================================================
;; Reloaded
;; =============================================================================


(def start #(mount/start-with-args {:env :dev}))


(def stop mount/stop)


(defn go []
  (start)
  (stest/instrument)
  :ready)


(defn reset []
  (stop)
  (refresh :after 'user/go))


;; =============================================================================
;; Figwheel
;; =============================================================================


(defn start-figwheel! [& builds]
  (when-not (ra/figwheel-running?)
    (timbre/debug "starting figwheel server...")
    (apply ra/start-figwheel! builds)))


(defn cljs-repl []
  (ra/cljs-repl "apply"))
