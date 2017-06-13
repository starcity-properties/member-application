(ns apply.logistics.events
  (:require [apply.prompts.models :as prompts]
            [clojure.set :as set]
            [plumbing.core :as plumbing]
            [re-frame.core :refer [reg-event-db]]))

;; Triggered when a community is selected. Either add it to the set, or remove
;; it from the set held in `:logistics/communities`.
(reg-event-db
 :logistics.communities/select
 (fn [db [_ community-name checked?]]
   (let [path [:logistics/communities :local :communities]]
     (if checked?
       (update-in db path conj community-name)
       (update-in db path set/difference #{community-name})))))

(reg-event-db
 :logistics.license/select
 (fn [db [_ license]]
   (assoc-in db [:logistics/license :local :license] license)))

(reg-event-db
 :logistics.move-in-date/choose
 (fn [db [_ move-in-date]]
   (assoc-in db [:logistics/move-in-date :local :move-in-date] move-in-date)))

;; =====================================
;; Pets

(reg-event-db
 :logistics.pets/has-pet
 (fn [db [_ has-pet]]
   (let [db' (-> (assoc-in db [:logistics/pets :local :has-pet] has-pet)
                 (plumbing/dissoc-in [:logistics/pets :local :pet-type]))]
     (if has-pet
       (assoc-in db' [:logistics/pets :local :pet-type] "dog")
       db'))))

(reg-event-db
 :logistics.pets/weight
 (fn [db [_ weight]]
   (assoc-in db [:logistics/pets :local :weight] weight)))

(reg-event-db
 :logistics.pets/breed
 (fn [db [_ breed]]
   (assoc-in db [:logistics/pets :local :breed] breed)))

;; =============================================================================
;; Parsers

(reg-event-db
 :logistics/parse
 (fn [db [_ {:keys [license move-in-date communities pet] :as data}]]
   (merge
    db
    (prompts/syncify
     {:logistics/communities  {:communities (set communities)}
      :logistics/license      {:license license}
      :logistics/move-in-date {:move-in-date move-in-date}
      :logistics/pets         {:has-pet  (:has-pet pet)
                               :pet-type (:pet-type pet)
                               :breed    (:breed pet)
                               :weight   (:weight pet)}}))))
