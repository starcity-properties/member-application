(ns apply.finish.events
  (:require [apply.prompts.models :as prompts]
            [apply.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   inject-cofx]]
            [day8.re-frame.http-fx]
            [starcity.log :as l]
            [ajax.core :as ajax]
            [apply.notifications :as n]
            [clojure.string :as string]
            [plumbing.core :as plumbing]))

;; =============================================================================
;; Editing

(reg-event-db
 :finish.referral/choose
 (fn [db [_ source]]
   (assoc-in db [:finish/pay :referral/source] source)))

(reg-event-db
 :finish.pay/toggle-agree
 (fn [db _]
   (update-in db [:finish/pay :agreed-to-terms] not)))

(reg-event-fx
 :finish/begin-checkout
 (fn [{:keys [db]} _]
   (let [agreed-to-terms (get-in db [:finish/pay :agreed-to-terms])]
     ;; only allow the stripe checkout when terms have been agreed to
     (when agreed-to-terms
       {:stripe-checkout {:on-success [:finish/submit-payment]}}))))

(reg-event-fx
 :finish/submit-payment
 (fn [{:keys [db]} [_ token]]
   {:db         (assoc db :prompt/loading true)
    :http-xhrio {:method          :post
                 :uri             "/api/submit-payment"
                 :params          (let [s  (get-in db [:finish/pay :referral/source])
                                        s' (when-not (string/blank? s) s)]
                                    (plumbing/assoc-when
                                     {:token (:id token)}
                                     :referral s'))
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-failure      [:finish.submit-payment/failure]
                 :on-success      [:finish.submit-payment/success]}}))

(def ^:private payment-error-msg
  "Something went wrong while processing your payment. Please try again.")

(reg-event-fx
 :finish.submit-payment/failure
 (fn [{:keys [db]} [_ err]]
   (l/error "Error encountered while submitting payment" err)
   {:db       (assoc db :prompt/loading false)
    :dispatch [:app/notify (n/error payment-error-msg)]}))

;; On success, just transition the URL to the "complete" view
;; There's no need for actually doing validation that the application has been
;; successfully completed, since the URL is not exposed anywhere.
(reg-event-fx
 :finish.submit-payment/success
 (fn [{:keys [db]} [_ result]]
   {:db    (assoc db :prompt/loading false)
    :route (routes/complete)}))
