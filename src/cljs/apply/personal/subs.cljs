(ns apply.personal.subs
  (:require [apply.prompts.models :as prompts]
            [re-frame.core :refer [reg-sub]]
            [bouncer.core :as b]
            [bouncer.validators :as v]))

;; =============================================================================
;; Phone number

(reg-sub
 :personal/phone-number
 (fn [db _]
   (get db :personal/phone-number)))

(reg-sub
 :personal.phone-number/form-data
 :<- [:personal/phone-number]
 (prompts/form-data :phone-number))

(def ^:private phone-number-complete?
  (comp not empty?))

(reg-sub
 :personal.phone-number/complete?
 :<- [:personal/phone-number]
 (prompts/complete-when phone-number-complete? :phone-number))

;; =============================================================================
;; Background

(reg-sub
 :personal/background
 (fn [db _]
   (get db :personal/background)))

(reg-sub
 :personal.background/form-data
 :<- [:personal/background]
 (prompts/form-data))

(defn- background-complete?
  [background-info]
  (b/valid?
   background-info
   {:dob     v/required
    :name    {:first v/required
              :last  v/required}
    :address {:locality    v/required
              :region      v/required
              :country     v/required
              :postal-code v/required}
    :consent [v/required true?]}))

(reg-sub
 :personal.background/complete?
 :<- [:personal/background]
 (prompts/complete-when background-complete?))

;; =============================================================================
;; Income

(reg-sub
 :personal/income
 (fn [db _]
   (get db :personal/income)))

(reg-sub
 :personal.income/form-data
 :<- [:personal/income]
 (prompts/form-data))

(defn- income-complete? [data]
  (if (map? data)
    (not-empty (:paths data))
    (not (nil? data))))

(reg-sub
 :personal.income/complete?
 :<- [:personal/income]
 (prompts/complete-when income-complete?))
