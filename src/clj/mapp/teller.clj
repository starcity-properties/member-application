(ns mapp.teller
  (:require [mapp.config :as config :refer [config]]
            [mapp.datomic]
            [teller.core :as teller]
            [mount.core :refer [defstate]]
            [toolbelt.datomic :as td]
            [toolbelt.datomic.schema :as tds]
            [taoensso.timbre :as timbre]))



(defstate teller
  :start (let [dt   (teller/datomic-connection
                     (:uri (config/datomic config))
                     (config/datomic-partition config))
               st   (teller/stripe-connection
                     (config/stripe-secret-key config))
               conn (teller/connection dt st)]
           (timbre/info "connecting to teller...")
           (teller/connect conn))
  :stop (do
          (timbre/info "disconnecting from teller...")
          (teller/disconnect teller)))
