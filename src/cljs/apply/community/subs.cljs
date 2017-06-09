(ns apply.community.subs
  (:require [apply.prompts.models :as prompts]
            [re-frame.core :refer [reg-sub]]
            [clojure.string :as s]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Helpers

(def ^:private long-enough 0)

;; NOTE: 20 is kind of arbitrary
(defn answer-long-enough? [s]
  (when-let [s s]
    (let [s' (s/replace s #"\s" "")]
      (> (count s') long-enough))))

(defn about-you-complete? [{:keys [free-time dealbreakers]}]
  ;; NOTE: dealbreakers isn't really required.
  (answer-long-enough? free-time))

(defn communal-living-complete?
  [{:keys [prior-experience skills conflicts]}]
  (and (answer-long-enough? prior-experience)
       (answer-long-enough? skills)
       (answer-long-enough? conflicts)))

;; =============================================================================
;; Why Starcity

(reg-sub
 :community/why-starcity
 (fn [db _]
   (get db :community/why-starcity)))

(reg-sub
 :community.why-starcity/form-data
 :<- [:community/why-starcity]
 (prompts/form-data :why-starcity))

(reg-sub
 :community.why-starcity/complete?
 :<- [:community/why-starcity]
 (prompts/complete-when not-empty :why-starcity))

;; =============================================================================
;; About you

(reg-sub
 :community/about-you
 (fn [db _]
   (get db :community/about-you)))

(reg-sub
 :community.about-you/form-data
 :<- [:community/about-you]
 (prompts/form-data))

(reg-sub
 :community.about-you/complete?
 :<- [:community/about-you]
 (prompts/complete-when about-you-complete?))

;; =============================================================================
;; Communal Living

(reg-sub
 :community/communal-living
 (fn [db _]
   (get db :community/communal-living)))

(reg-sub
 :community.communal-living/form-data
 :<- [:community/communal-living]
 (prompts/form-data))

(reg-sub
 :community.communal-living/complete?
 :<- [:community/communal-living]
 (prompts/complete-when communal-living-complete?))
