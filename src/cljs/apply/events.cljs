(ns apply.events
  (:require [apply.prompts.events]
            [apply.logistics.events]
            [apply.personal.events]
            [apply.community.events]
            [apply.finish.events]
            [apply.complete.events]
            [apply.prompts.models :as prompts]
            [apply.routes :as routes]
            [apply.db :refer [default-value]]
            [apply.api :as api]
            [day8.re-frame.http-fx]
            [starcity.re-frame.chatlio-fx]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   reg-fx]]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]
            [apply.notifications :as n]))

;; =============================================================================
;; App Events
;; =============================================================================

;; 1. Initialialize the app by loading the default values for the DB
;; 2. Pull server-side data
(reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db       default-value
    :dispatch [:app.initialize/fetch]}))

;; Pull current progress
(reg-event-fx
 :app.initialize/fetch
 (fn [{:keys [db]} _]
   {:db         (assoc db :app/initializing true)
    :http-xhrio {:method          :get
                 :uri             (api/route)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:app.initialize.fetch/success]
                 :on-failure      [:app.initialize.fetch/fail]}}))


(reg-event-fx
 :app.initialize.fetch/success
 (fn [{:keys [db]} [_ {:keys [properties stripe licenses] :as result}]]
   {:db            (merge db {:app/properties   (sort-by :property/available-on properties)
                              :app/licenses     (sort-by :license/term > licenses)
                              :app/initializing false})
    :chatlio/ready [:init-chatlio result]
    :dispatch      [:app/parse result]}))

(reg-event-fx
 :init-chatlio
 (fn [_ [_ {account :account}]]
   {:chatlio/show     false
    :chatlio/identify [(:account/email account) {:name (:account/name account)}]}))

(reg-event-fx
 :app/parse
 (fn [{db :db} [_ {:keys [payment-allowed complete] :as result}]]
   (if complete
     ;; If the complete flag is set, navigate to the completion page
     {:route (routes/complete)}
     ;; Otherwise do the normal thing and parse the prompt form data
     {:db         (-> (prompts/complete db payment-allowed)
                      (assoc :app/complete false))
      :dispatch-n [[:logistics/parse result]
                   [:personal/parse result]
                   [:community/parse result]]})))

(reg-event-fx
 :app.initialize.fetch/fail
 (fn [{:keys [db]} [_ err]]
   {:dispatch [:app/notify (n/error "Error encountered during initialization!")]
    :db       (assoc db :app/initializing false)}))

(reg-event-db
 :app/notify
 (fn [db [_ n-or-ns]]
   (assoc db :app/notifications
          (if (sequential? n-or-ns)
            (if (vector? n-or-ns) n-or-ns (vec n-or-ns))
            [n-or-ns]))))

;; =============================================================================
;; Notifications
;; =============================================================================

;; Delete a notification by index
(reg-event-db
 :notification/delete
 (fn [db [_ idx]]
   (update db :app/notifications tb/remove-at idx)))

(reg-event-db
 :notification/clear-all
 (fn [db _]
   (assoc db :app/notifications [])))
