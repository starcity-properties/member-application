(ns apply.community.events
  (:require [re-frame.core :refer [reg-event-db debug]]
            [starcity.log :as l]
            [apply.prompts.models :as prompts]))

(reg-event-db
 :community/why-starcity
 (fn [db [_ new-val]]
   (assoc-in db [:community/why-starcity :local :why-starcity] new-val)))

(reg-event-db
 :community/about-you
 (fn [db [_ k new-val]]
   (assoc-in db [:community/about-you :local k] new-val)))

(reg-event-db
 :community/communal-living
 (fn [db [_ k new-val]]
   (assoc-in db [:community/communal-living :local k] new-val)))

;; =============================================================================
;; Parser

(reg-event-db
 :community/parse
 (fn [db [_ {:keys [fitness]}]]
   (let [{:keys [:fitness/interested
                 :fitness/free-time
                 :fitness/dealbreakers
                 :fitness/skills
                 :fitness/experience]} fitness]
     (merge
      db
      (prompts/syncify
       {:community/why-starcity    {:why-starcity interested}
        :community/about-you       {:free-time    free-time
                                    :dealbreakers dealbreakers}
        :community/communal-living {:skills           skills
                                    :prior-experience experience}})))))
