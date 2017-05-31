(ns apply.overview.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
 :overview.welcome/complete?
 (fn [_ _]
   {:local true :remote false}))

(reg-sub
 :overview.advisor/complete?
 (fn [_ _]
   {:local true :remote false}))
