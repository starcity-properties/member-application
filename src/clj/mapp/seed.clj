(ns mapp.seed
  (:require [mapp.config :as config :refer [config]]
            [io.rkn.conformity :as cf]
            [datomic.api :as d]))

(def password
  "bcrypt+blake2b-512$30e1776f40ee533841fcba62a0dbd580$12$2dae523ec1eb9fd91409ebb5ed805fe53e667eaff0333243")

(defn accounts []
  [{:db/id              (d/tempid (config/datomic-partition config))
    :account/email      "test@test.com"
    :account/password   password
    :account/first-name "Josh"
    :account/last-name  "Lehman"
    :account/role       :account.role/applicant
    :account/activated  true}])

(defn applications []
  (let [id (d/tempid (config/datomic-partition config))]
    [{:db/id               [:account/email "test@test.com"]
      :account/application id}
     {:db/id              id
      :application/status :application.status/in-progress}]))

(defn seed [conn]
  (cf/ensure-conforms
   conn
   {:seed/accounts     {:txes [(accounts)]}
    :seed/applications {:txes     [(applications)]
                        :requires [:seed/accounts]}}))
