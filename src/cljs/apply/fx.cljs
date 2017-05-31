(ns apply.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            [apply.routes :refer [navigate!]]))

;; Allow route changes to be expressed as events
(reg-fx
 :route
 (fn [new-route]
   (navigate! new-route)))

(reg-fx
 :stripe-checkout
 (fn [{:keys [on-success]}]
   (let [checkout (aget js/window "StripeCheckout")
         conf    {:name            "Starcity"
                  :description     "Member Application"
                  :amount          (.-amount js/stripe)
                  :key             (.-key js/stripe)
                  :zipCode         true
                  :allowRememberMe true
                  :locale          "auto"
                  :token           (fn [token]
                                     (dispatch (conj on-success (js->clj token :keywordize-keys true))))}]
     ((aget checkout "open") (clj->js conf)))))
