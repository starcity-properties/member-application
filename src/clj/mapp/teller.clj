(ns mapp.teller
  (:require [mapp.config :as config :refer [config]]
            [mapp.datomic]
            [teller.core :as teller]
            [mount.core :refer [defstate]]
            [toolbelt.datomic :as td]
            [toolbelt.datomic.schema :as tds]))



(defstate teller
  :start (let [conn (teller/datomic-connection (:uri (config/datomic config))
                                               (config/stripe-secret-key config))]
           (tds/set-partition! (config/datomic-partition config))
           (teller/connect conn))
  :stop (teller/disconnect teller))
