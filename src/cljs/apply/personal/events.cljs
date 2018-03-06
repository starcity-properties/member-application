(ns apply.personal.events
  (:require [apply.prompts.models :as prompts]
            [re-frame.core :refer [reg-event-db debug]]
            [clojure.spec.alpha :as s]))

;; =============================================================================
;; Editing

(reg-event-db
 :personal.phone-number/change
 (fn [db [_ new-phone-number]]
   (assoc-in db [:personal/phone-number :local :phone-number] new-phone-number)))

(reg-event-db
 :personal.background/address
 (fn [db [_ change-key data]]
   (assoc-in db [:personal/background :local :address change-key] data)))

(reg-event-db
 :personal.background/name
 (fn [db [_ change-key data]]
   (assoc-in db [:personal/background :local :name change-key] data)))

(reg-event-db
 :personal.background/consent
 (fn [db [_ consent]]
   (assoc-in db [:personal/background :local :consent] consent)))

(reg-event-db
 :personal.background/dob
 (fn [db [_ dob]]
   (assoc-in db [:personal/background :local :dob] dob)))

(defn- files->form-data [files]
  (let [form-data (js/FormData.)]
    (doseq [file-key (.keys js/Object files)]
      (let [file (aget files file-key)]
        (.append form-data "files[]" file (.-name file))))
    form-data))

(reg-event-db
 :personal.income/file-picked
 (fn [db [_ files]]
   (assoc-in db [:personal/income :local] (files->form-data files))))

;; =============================================================================
;; Parser

(s/def ::name
  (s/keys :req-un [::first ::last]
          :opt-un [::middle]))

(s/def ::account
  (s/keys :req-un [::name]))

(s/def ::background
  (s/keys :req-un [::name ::consent]))

(defn- background
  [account community-safety {:keys [locality region country postal-code]}]
  {:name    (:name account)
   :dob     (:dob account)
   :consent (:consent community-safety)
   :address {:locality    locality
             :region      region
             :country     (or country "US") ; Set to US by default
             :postal-code postal-code}})

(s/fdef background
        :args (s/cat :account ::account)
        :ret ::background)

(reg-event-db
 :personal/parse
 (fn [db [_ {:keys [account community-safety address income-file-paths]}]]
   (merge
    db
    (prompts/syncify
     {:personal/phone-number {:phone-number (get account :phone-number "")}
      :personal/background   (background account community-safety address)
      :personal/income       {:paths income-file-paths}}))))
