(ns apply.finish.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
 :finish/referral-sources
 (fn [db _]
   (js->clj (aget js/window "referral_sources"))))

(reg-sub
 :finish.referral/source
 (fn [db _]
   (get-in db [:finish/pay :referral/source])))

(reg-sub
 :finish.pay/complete?
 (fn [db _]
   (let [agreed-to-terms (get-in db [:finish/pay :agreed-to-terms])]
     {:local agreed-to-terms :remote false})))

(reg-sub
 :finish.pay.terms/agreed?
 (fn [db _]
   (get-in db [:finish/pay :agreed-to-terms])))
