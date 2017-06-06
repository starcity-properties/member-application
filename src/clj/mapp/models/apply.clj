(ns mapp.models.apply
  (:refer-clojure :exclude [update])
  (:require [blueprints.models
             [account :as account]
             [application :as application]
             [charge :as charge]
             [customer :as customer]
             [license :as license]
             [property :as property]]
            [clj-time.coerce :as c]
            [clojure
             [set :as set]
             [spec :as s]]
            [datomic.api :as d]
            [mapp.config :as config :refer [config]]
            [plumbing.core :as plumbing]
            [ribbon.charge :as rch]
            [ribbon.customer :as rcu]
            [toolbelt
             [async :as async]
             [predicates :as p]]))

;; =============================================================================
;; Constants
;; =============================================================================

(def application-fee
  "The application fee in cents."
  2500)

(def ^:private application-fee-dollars
  (float (/ application-fee 100)))

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
              [:pet/type :pet/breed :pet/weight]}
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
  (-> {:name (plumbing/assoc-when
              {:first first-name
               :last  last-name}
              :middle middle-name)}
      (plumbing/assoc-when :phone-number phone-number :dob dob)))

(defmethod parse :communities [_ _ data]
  (let [communities (get-in data [:account/application :application/communities])]
    (map :property/internal-name communities)))

(defmethod parse :license [_ _ data]
  (get-in data [:account/application :application/license :db/id]))

(defmethod parse :move-in-date
  [_ _ data]
  (get-in data [:account/application :application/move-in]))

(defmethod parse :pet [_ _ data]
  (let [{:keys [:pet/type :pet/breed :pet/weight] :as pet}
        (get-in data [:account/application :application/pet])]
    (if (nil? (get-in data [:account/application :application/has-pet]))
      {}
      (plumbing/assoc-when {:has-pet (boolean pet)}
                           :pet-type (when type (name type))
                           :breed breed
                           :weight weight))))

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

;; NOTE: Using spec for this since it's quick. Might not be the best.
;; I'm really just checking types of the resulting "progress", but the big
;; assumption here is that all of the data was properly validated before going
;; into the database. Since this validation is only of data that's come out of
;; the db, it shouldn't need another serious validation pass.

(def ^:private non-empty-string? (comp not clojure.string/blank?))

(s/def ::communities (s/+ string?))
(s/def ::license integer?)
(s/def ::move-in-date inst?)
;; pet
(s/def ::weight integer?)
(s/def ::breed string?)
(s/def ::pet-type #{"cat" "dog"})
(s/def ::has-pet boolean?)
(s/def ::pet (s/keys :req-un [::has-pet] :opt-un [::pet-type ::breed ::weight]))

;; background
(s/def ::consent true?)
(s/def ::community-safety (s/keys :req-un [::consent]))

;; address
;; NOTE: This doesn't work because the s/def happens before the state has mounted
;; (s/def ::country countries/codes)
(s/def ::locality non-empty-string?)
(s/def ::region non-empty-string?)
(s/def ::postal-code non-empty-string?)
(s/def ::address (s/keys :req-un [::locality ::region ::postal-code ::country]))

;; Income
(s/def ::income-file-paths (s/+ string?))

;; Community fitness
(s/def :fitness/free-time non-empty-string?)
(s/def :fitness/skills non-empty-string?)
(s/def :fitness/experience non-empty-string?)
(s/def :fitness/interested non-empty-string?)
(s/def :fitness/dealbreakers non-empty-string?)
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

(comment

  (let [db (d/db mapp.datomic/conn)
        account (account/by-email db "test@test.com")]
    @(d/transact mapp.datomic/conn [{:db/id (:db/id (application/by-account db account))
                                     :application/status :application.status/in-progress}]))

  )

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
        old-values (set (map #(if (p/entity? %) (:db/id %) %) (get ent attribute)))
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
  [[:db/add (:db/id application) :application/move-in (c/to-date date)]])

(defmethod update-tx :logistics/pets
  [_ {:keys [has-pet pet-type breed weight]} application _]
  (let [pet (get application :application/pet)]
    (-> (cond
          ;; Indicated that they have a pet AND there's already a pet entiti -- this is an update
          (and has-pet pet)             [(plumbing/assoc-when
                                          {:db/id    (:db/id pet)
                                           :pet/type (keyword pet-type)}
                                          :pet/breed breed :pet/weight weight)]
          ;; Indicated that they do not have a pet, but previously had one. Retract pet entity.
          (and (not has-pet) pet)       [[:db.fn/retractEntity (:db/id pet)]]
          ;; Indicated that they have a pet, but previously did not. Create pet entity
          (and has-pet (not pet))       [{:db/id           (:db/id application)
                                          :application/pet (plumbing/assoc-when
                                                            {:pet/type (keyword pet-type)}
                                                            :pet/breed breed :pet/weight weight)}]
          ;; Indicated that they have no pet, and had no prior pet. Do nothing.
          (and (not has-pet) (not pet)) [])
        ;; Update application flag.
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
  (->> (plumbing/assoc-when {:fitness/free-time free-time}
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

(defn- create-customer! [token account]
  (let [key (config/stripe-secret-key config)]
    (async/<!!? (rcu/create! key (account/email account) token))))

(s/fdef create-customer!
        :args (s/cat :token string? :account p/entity?)
        :ret (s/and map? rcu/customer?))


(defn- create-charge! [token account customer]
  (let [key (config/stripe-secret-key config)]
    (:id (async/<!!? (rch/create! key application-fee (rcu/default-source customer)
                                  :customer-id (rcu/customer-id customer)
                                  :email (account/email account)
                                  :description (format "application fee for %s"
                                                       (account/email account)))))))

(s/fdef create-charge!
        :args (s/cat :token string?
                     :account p/entity?
                     :customer-id rcu/customer?)
        :ret string?)


(defn- charge! [token account]
  (let [cus (create-customer! token account)]
    [(rcu/customer-id cus) (create-charge! token account cus)]))


(defn submit!
  "Submit the member application."
  [conn token account]
  (let [[cus-id cha-id] (charge! token account)
        app             (application/by-account (d/db conn) account)]
    @(d/transact conn [[:db.application/submit (:db/id app)]
                       (charge/create cha-id application-fee-dollars)
                       (customer/create cus-id account)])))


(comment
  (rcu/default-source (async/<!!? (rcu/fetch (config/stripe-secret-key config) "cus_AkmQzv63frpu1c")))

  (rcu/source? {})

  )
