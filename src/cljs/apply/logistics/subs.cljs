(ns apply.logistics.subs
  (:require [apply.prompts.models :as prompts]
            [re-frame.core :refer [reg-sub]]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [starcity.dates :as dates]
            [starcity.log :as l]))


;; =============================================================================
;; Communities

(defn- format-date [date]
  (if (t/before? (c/to-date-time date) (t/now))
    "now"
    (dates/format :medium-date (c/to-local-date date))))

(defn- parse-property
  [{:keys [:property/name
           :property/internal-name
           :property/units
           :property/upcoming
           :property/available-on]}]
  {:name          name
   :internal-name internal-name
   :num-units     units
   :available-on  (or upcoming (format-date available-on))})

(reg-sub
 :logistics/available-communities
 (fn [{properties :app/properties} _]
   (map parse-property properties)))

(reg-sub
 :logistics/communities
 (fn [db _]
   (get db :logistics/communities)))

(reg-sub
 :logistics.communities/form-data
 :<- [:logistics/communities]
 (prompts/form-data :communities))

(reg-sub
 :logistics.communities/complete?
 :<- [:logistics/communities]
 (prompts/complete-when not-empty :communities))

;; =============================================================================
;; License

;; The available license
(reg-sub
 :logistics.license/available-licenses
 (fn [{licenses :app/licenses} _]
   (map
    (fn [{:keys [:license/term :db/id]}]
      {:id id :term term})
    licenses)))

(defn- associative-licenses
  [properties]
  (letfn [(-group [licenses]
            (group-by (comp :db/id :property-license/license) licenses))
          (-property-tf [p]
            (update p :property/licenses -group))]
    (map -property-tf properties)))

(reg-sub
 :logistics.license/license-prices
 (fn [{:keys [:app/licenses :app/properties]} _]
   (letfn [(-license-price [property {id :db/id}]
             (let [{:keys [:property-license/base-price]} (get-in property [:property/licenses id 0])]
               {:license-id id :price base-price}))]
     (map
      (fn [{:keys [:property/name :property/internal-name] :as p}]
        {:name          name
         :internal-name internal-name
         :prices        (map (partial -license-price p) licenses)})
      (associative-licenses properties)))))

(reg-sub
 :logistics/license
 (fn [db _]
   (get db :logistics/license)))

(reg-sub
 :logistics.license/form-data
 :<- [:logistics/license]
 (prompts/form-data :license))

(reg-sub
 :logistics.license/complete?
 :<- [:logistics/license]
 (prompts/complete-when (comp not nil?) :license))

;; =============================================================================
;; Move-in Date

(reg-sub
 :logistics/move-in-date
 (fn [db _]
   (get db :logistics/move-in-date)))

(reg-sub
 :logistics.move-in-date/form-data
 :<- [:logistics/move-in-date]
 (prompts/form-data :move-in-date))

(reg-sub
 :logistics.move-in-date/complete?
 :<- [:logistics/move-in-date]
 (prompts/complete-when (comp not nil?) :move-in-date))

;; =============================================================================
;; Pets

(reg-sub
 :logistics/pets
 (fn [db _]
   (get db :logistics/pets)))

(reg-sub
 :logistics.pets/form-data
 :<- [:logistics/pets]
 (prompts/form-data))

(defn- pets-complete? [{:keys [has-pet pet-type breed weight]}]
  (cond
    (false? has-pet) true
    (nil? has-pet)   false
    :otherwise       (if (= pet-type "dog")
                       (and breed weight)
                       true)))

;; The completion logic can also by hooked in by passing pet data as a param
(reg-sub
 :logistics.pets/complete?
 :<- [:logistics/pets]
 (prompts/complete-when pets-complete?))
