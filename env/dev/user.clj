(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.spec.test :as stest]
            [clojure.spec :as s]
            [datomic.api :as d]
            [figwheel-sidecar.repl-api :as ra]
            [taoensso.timbre :as timbre]
            [mount.core :as mount :refer [defstate]]
            [mapp.config]
            [mapp.server]
            [mapp.log]
            [mapp.datomic :refer [conn]]))

(timbre/refer-timbre)

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
;; CLJS

(defn start-figwheel! [& builds]
  (when-not (ra/figwheel-running?)
    (timbre/debug "starting figwheel server...")
    (apply ra/start-figwheel! builds)))

(defn cljs-repl []
  (ra/cljs-repl "apply"))
