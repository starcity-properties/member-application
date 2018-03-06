(ns user
  (:require [clojure.core.async :as a]
            [clojure.spec.test.alpha :as stest]
            [clojure.tools.namespace.repl :refer [refresh]]
            [datomic.api :as d]
            [figwheel-sidecar.repl-api :as ra]
            [mapp.config :as config :refer [config]]
            [mapp.datomic :refer [conn]]
            [mapp.core]
            [mount.core :as mount :refer [defstate]]
            [reactor.reactor :as reactor]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)


;; =============================================================================
;; Reactor
;; =============================================================================


(defn mailgun-domain
  [config]
  (get-in config [:mailgun :domain]))


(defn mailgun-sender
  [config]
  (get-in config [:mailgun :sender]))


(defn mailgun-api-key
  [config]
  (get-in config [:mailgun :api-key]))


(defn slack-webhook-url
  [config]
  (get-in config [:secrets :slack :webhook]))


(defn slack-username
  [config]
  (get-in config [:slack :username]))


(defstate reactor
  :start (let [conf {:mailer             {:api-key (mailgun-api-key config)
                                          :domain  (mailgun-domain config)
                                          :sender  (mailgun-sender config)
                                          :send-to "josh@starcity.com"}
                     :slack              {:webhook-url (slack-webhook-url config)
                                          :username    (slack-username config)
                                          :channel     "#debug"}
                     :stripe             {:secret-key (config/stripe-secret-key config)}
                     :public-hostname    "http://localhost:8080"
                     :dashboard-hostname "http://localhost:8082"}
               chan (a/chan (a/sliding-buffer 512))]
           (reactor/start! conn chan conf))
  :stop (reactor/stop! reactor))


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
