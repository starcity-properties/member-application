(ns mapp.models.apply
  (:refer-clojure :exclude [update])
  (:require [blueprints.models.account :as account]
            [blueprints.models.application :as application]
            [blueprints.models.license :as license]
            [blueprints.models.property :as property]
            [blueprints.models.referral :as referral]
            [clj-time.coerce :as c]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [datomic.api :as d]
            [mapp.config :as config :refer [config]]
            [mapp.teller :refer [teller]]
            [taoensso.timbre :as timbre]
            [teller.customer :as customer]
            [teller.payment :as payment]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]))

;; =============================================================================
;; Constants
;; =============================================================================


(def application-fee
  "The application fee in dollars."
  25.0)


;; =============================================================================
;; Progress
;; =============================================================================


(defn- fetch [db account]
  (d/pull db
          [:account/first-name
           :account/last-name
           :account/middle-name
           :account/phone-number
           :account/dob
           {:income-file/_account [:income-file/path]}
           {:community-safety/_account
            [:db/id :community-safety/consent-given?]}
           {:account/application
            [{:application/communities
              [:property/internal-name]}
             {:application/license [:db/id]}
             :application/move-in
             :application/has-pet
             {:application/pet
              [:pet/type
               :pet/breed
               :pet/weight
               :pet/daytime-care
               :pet/demeanor
               :pet/sterile
               :pet/vaccines
               :pet/bitten]}
             {:application/address
              [:address/region :address/locality :address/postal-code :address/country]}
             {:application/fitness
              [:fitness/interested
               :fitness/free-time
               :fitness/dealbreakers
               :fitness/skills
               :fitness/experience
               :fitness/conflicts]}
             :application/status
             :db/id]}]
          (:db/id account)))


(defmulti parse (fn [db key data] key))


;; =====================================
;; Account

(defmethod parse :account
  [_ _ {:keys [:account/first-name
               :account/last-name
               :account/middle-name
               :account/phone-number
               :account/dob]}]
  (-> {:name (tb/assoc-when
              {:first first-name
               :last  last-name}
              :middle middle-name)}
      (tb/assoc-when :phone-number phone-number :dob dob)))


(defmethod parse :communities [_ _ data]
  (let [communities (get-in data [:account/application :application/communities])]
    (map :property/internal-name communities)))


(defmethod parse :license [_ _ data]
  (get-in data [:account/application :application/license :db/id]))


(defmethod parse :move-in-date
  [_ _ data]
  (get-in data [:account/application :application/move-in]))


(defmethod parse :pet [_ _ data]
  (let [{:keys [pet/type pet/breed pet/weight pet/daytime-care pet/demeanor
                pet/vaccines pet/sterile pet/bitten] :as pet}
        (get-in data [:account/application :application/pet])]
    (if-some [has-pet (get-in data [:account/application :application/has-pet])]
      (tb/assoc-some
       {:has-pet has-pet}
       :pet-type (when (some? type) (name type))
       :breed breed
       :weight weight
       :daytime-care daytime-care
       :demeanor demeanor
       :vaccines vaccines
       :sterile sterile
       :bitten bitten)
      {})))


(defmethod parse :community-safety [_ _ data]
  {:consent (get-in data [:community-safety/_account 0 :community-safety/consent-given?])})


(defmethod parse :address [_ _ data]
  (let [address (get-in data [:account/application :application/address])]
    {:locality    (:address/locality address)
     :region      (:address/region address)
     :postal-code (:address/postal-code address)
     :country     (:address/country address)}))


(defmethod parse :income-file-paths [_ _ data]
  (let [files (:income-file/_account data)]
    (map :income-file/path files)))


(defmethod parse :fitness [_ _ data]
  (get-in data [:account/application :application/fitness]))


(defmethod parse :complete [db _ data]
  (let [application (d/entity db (get-in data [:account/application :db/id]))]
    (and application (not (application/in-progress? application)))))

;; =====================================
;; Completeness


(def ^:private notblank? (comp not clojure.string/blank?))

(s/def ::communities (s/+ string?))
(s/def ::license integer?)
;; pet
(s/def ::weight integer?)
(s/def ::breed string?)
(s/def ::pet-type #{"cat" "dog"})
(s/def ::has-pet boolean?)
(s/def ::pet (s/keys :req-un [::has-pet] :opt-un [::pet-type ::breed ::weight]))

(s/def ::consent true?)
(s/def ::community-safety (s/keys :req-un [::consent]))

;; NOTE: This doesn't work because the s/def happens before the state has mounted
;; (s/def ::country countries/codes)
(s/def ::locality notblank?)
(s/def ::region notblank?)
(s/def ::postal-code notblank?)
(s/def ::address (s/keys :req-un [::locality ::region ::postal-code ::country]))

(s/def ::income-file-paths (s/+ string?))

(s/def :fitness/free-time notblank?)
(s/def :fitness/skills notblank?)
(s/def :fitness/experience notblank?)
(s/def :fitness/interested notblank?)
(s/def :fitness/dealbreakers notblank?)
(s/def ::fitness
  (s/keys :req [:fitness/free-time
                :fitness/skills
                :fitness/experience
                :fitness/interested]
          :opt [:fitness/dealbreakers]))


(defn- payment-allowed?* [progress]
  (s/valid? (s/keys :req-un [::communities ::license ::move-in-date ::community-safety
                             ::address ::income-file-paths ::fitness ::pet])
            progress))


(def ^:private top-level-keys
  [:account :communities :license :move-in-date :pet :community-safety
   :address :income-file-paths :fitness :complete])


(defn progress
  "Produce `account`'s progress in the member application."
  [db account]
  (let [data     (fetch db account)
        progress (reduce
                  (fn [acc k]
                    (assoc acc k (parse db k data)))
                  {}
                  top-level-keys)]
    (assoc progress :payment-allowed (payment-allowed?* progress))))


(defn finished?
  "Is `account` finished with the member application?"
  [db account]
  (payment-allowed?* (progress db account)))


;; =============================================================================
;; Bootstrap
;; =============================================================================


(defn- properties [db]
  (d/q '[:find [?e ...] :where [?e :property/name _]] db))


(defn- clientize-property-license [license]
  (-> (select-keys license [:property-license/license
                            :property-license/base-price
                            :db/id])
      (clojure.core/update :property-license/license select-keys [:db/id :license/term])))


(defn- clientize-property [db property-id]
  (let [property (d/entity db property-id)]
    {:db/id                  (:db/id property)
     :property/name          (:property/name property)
     :property/internal-name (:property/internal-name property)
     :property/available-on  (:property/available-on property)
     :property/licenses      (map clientize-property-license (:property/licenses property))
     :property/units         (count (property/available-units db property))}))


(defn- initial-data
  "Required information to the application client."
  [db account]
  {:properties (map (partial clientize-property db) (properties db))
   :licenses   (map (fn [l] {:license/term (:license/term l)
                            :db/id        (:db/id l)})
                    (license/available db))
   :account    {:db/id         (:db/id account)
                :account/name  (account/full-name account)
                :account/email (account/email account)}})


(defn bootstrap
  "Bootstrap the app by fetching user's progress and initialization data."
  [db account]
  (merge (initial-data db account)
         (progress db account)))


;; =============================================================================
;; Update
;; =============================================================================


(defn- replace-unique
  "Given an entity-id, cardinality many attribute and new values, generate a
  transaction to remove all values that are not present in `new-values` and add
  any values that were not already present."
  [conn entity-id attribute new-values]
  (let [ent        (d/entity (d/db conn) entity-id)
        old-values (set (map #(if (td/entity? %) (:db/id %) %) (get ent attribute)))
        to-remove  (set/difference old-values (->> new-values
                                                   (map (comp :db/id (partial d/entity (d/db conn))))
                                                   set))]
    (vec
     (concat
      (map (fn [v] [:db/retract entity-id attribute v]) to-remove)
      (map (fn [v] [:db/add entity-id attribute v]) new-values)))))


(def account-id (comp :db/id application/account))


;; =====================================
;; tx generation


(defn- community-safety [application]
  (-> application :account/_application first :community-safety/_account first))


(defn- create-application-if-needed
  "Create an application for `account` if one does not already exist."
  [conn account]
  (when-not (application/by-account (d/db conn) account)
    @(d/transact
      conn
      [{:db/id                (d/tempid (config/datomic-partition config))
        :application/status   :application.status/in-progress
        :account/_application (:db/id account)}])))


(defmulti ^:private update-tx (fn [_ _ _ key] key))


(def ^:private communities->lookups
  (partial map (fn [c] [:property/internal-name c])))


(defmethod update-tx :logistics/communities
  [conn {communities :communities} application _]
  (replace-unique conn
                  (:db/id application)
                  :application/communities
                  (communities->lookups communities)))


(defmethod update-tx :logistics/license
  [_ {license :license} application _]
  [[:db/add (:db/id application) :application/license license]])


(defmethod update-tx :logistics/move-in-date
  [_ {date :move-in-date} application _]
  [[:db/add (:db/id application) :application/move-in (date/beginning-of-day (c/to-date date))]])


(defn- assemble-pet-tx
  [{:keys [has-pet pet-type breed weight daytime-care demeanor vaccines sterile bitten]
    :as   params}]
  (tb/assoc-some
   {:pet/type (keyword pet-type)}
   :pet/breed breed
   :pet/weight weight
   :pet/daytime-care daytime-care
   :pet/demeanor demeanor
   :pet/vaccines vaccines
   :pet/sterile sterile
   :pet/bitten bitten))


(defn- update-pet
  "Indicated that they have a pet AND there's already a pet entity -- this is an
  update."
  [{:keys [has-pet pet-type breed weight] :as params} pet]
  (when (and has-pet pet)
    [(-> (assemble-pet-tx params)
         (assoc :db/id (:db/id pet)))]))


(defn- retract-pet
  "Indicated that they do not have a pet, but previously had one. Retract pet
  entity."
  [params pet]
  (when (and (not (:has-pet params)) pet)
    [[:db.fn/retractEntity (:db/id pet)]]))


(defn- create-pet
  "Indicated that they have a pet, but previously did not. Create pet entity."
  [{has-pet :has-pet :as params} pet app]
  (when (and has-pet (not pet))
    [{:db/id           (:db/id app)
      :application/pet (assemble-pet-tx params)}]))


(defmethod update-tx :logistics/pets
  [_ {:keys [has-pet] :as params} application _]
  (let [pet (get application :application/pet)]
    (timbre/debug "pet params:" params (or (update-pet params pet)
                                           (retract-pet params pet)
                                           (create-pet params pet application)
                                           ;; Indicated that they have no pet, and had no prior pet. Do nothing.
                                           []))
    (-> (or (update-pet params pet)
            (retract-pet params pet)
            (create-pet params pet application)
            ;; Indicated that they have no pet, and had no prior pet. Do nothing.
            [])
        (conj [:db/add (:db/id application) :application/has-pet has-pet]))))


(defmethod update-tx :personal/phone-number
  [_ {phone-number :phone-number} application _]
  [[:db/add (account-id application) :account/phone-number phone-number]])


(defn- consent-tx
  [{consent :consent} application]
  (if-let [cs (community-safety application)]
    [[:db/add (:db/id cs) :community-safety/consent-given? consent]]
    [{:db/id                           (d/tempid (config/datomic-partition config))
      :community-safety/account        (account-id application)
      :community-safety/consent-given? consent}]))


(defn- account-tx
  [{{:keys [first middle last]} :name, dob :dob} application]
  (let [eid (account-id application)]
    [[:db/add eid :account/first-name first]
     (if (nil? middle)
       [:db/add eid :account/middle-name ""]
       [:db/add eid :account/middle-name middle])
     [:db/add eid :account/last-name last]
     [:db/add eid :account/dob (c/to-date dob)]]))


(defn- address-tx
  [{{:keys [region locality country postal-code]} :address} application]
  [{:db/id (:db/id application)
    :application/address
    {:address/region      region
     :address/locality    locality
     :address/country     country
     :address/postal-code postal-code}}])


(defmethod update-tx :personal/background
  [_ data application _]
  (vec (concat (consent-tx data application)
               (account-tx data application)
               (address-tx data application))))


(defn- community-fitness-id
  [application]
  (get-in application [:application/fitness :db/id]))


(defn- community-fitness-tx
  [application data]
  [(if-let [cfid (community-fitness-id application)]
     ;; Already a community fitness entity -- update it
     (merge {:db/id cfid} data)
     ;; Create a new community fitness entity (it's a component)
     {:db/id               (:db/id application)
      :application/fitness data})])


(defmethod update-tx :community/why-starcity
  [_ {:keys [why-starcity]} app _]
  (community-fitness-tx app {:fitness/interested why-starcity}))


;; dealbreakers are considered optional, and may be nil
(defmethod update-tx :community/about-you
  [_ {:keys [free-time dealbreakers]} app _]
  (->> (tb/assoc-when {:fitness/free-time free-time}
                            :fitness/dealbreakers dealbreakers)
       (community-fitness-tx app)))


(defmethod update-tx :community/communal-living
  [_ {:keys [prior-experience skills conflicts]} app _]
  (->> {:fitness/skills     skills
        :fitness/experience prior-experience
        :fitness/conflicts  conflicts}
       (community-fitness-tx app)))


;; =====================================
;; Update


(defn update
  "Update the member's application given client-side `data`, the `account` to
  update, and the `key` for which `data` belongs.."
  [conn data account key]
  (do
    (create-application-if-needed conn account)
    @(d/transact conn (update-tx conn data (application/by-account (d/db conn) account) key))))


;; =============================================================================
;; Submit
;; =============================================================================


(defn submit!
  "Submit the member application."
  [conn token account & [referral]]
  (let [app      (application/by-account (d/db conn) account)
        customer (customer/create! teller (account/email account)
                                   {:account     account
                                    :external-id (account/email account)
                                    :source      token})]
    (payment/create! teller customer application-fee :payment.type/application-fee
                     {:charge-now true})
    @(d/transact conn (tb/conj-when
                       (application/submit app)
                       (when-some [r referral] (referral/apply r account))))))


(comment

  (let [customer (customer/by-id teller "test@test.com")]
    (customer/fetch-source teller customer :payment.type/application-fee))


  (map d/touch (:payment/_customer (d/entity (d/db mapp.datomic/conn) 285873023223129)))



  )


(s/fdef submit!
        :args (s/cat :conn td/conn?
                     :token string?
                     :account td/entity?
                     :opts (s/? string?)))
