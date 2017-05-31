(ns mapp.datomic
  (:require [datomic.api :as d]
            [mount.core :as mount :refer [defstate]]
            [mapp.config :as config]
            [mapp.seed :as seed]
            [blueprints.core :as blueprints]
            [taoensso.timbre :as timbre]))

(defn- new-connection [{:keys [uri partition] :as conf}]
  (timbre/infof "connecting to Datomic database at uri %s..." uri)
  (d/create-database uri)
  (let [conn (d/connect uri)]
    (blueprints/conform-db conn partition)
    (when (#{:dev} (:env (mount/args)))
      (timbre/info "seeding database with dev data...")
      (seed/seed conn))
    conn))

(defn- disconnect [conn]
  (timbre/info "disconnecting from Datomic database...")
  (d/release conn))

;; =============================================================================
;; API
;; =============================================================================

(defstate conn
  :start (new-connection (config/datomic config/config))
  :stop  (disconnect conn))
