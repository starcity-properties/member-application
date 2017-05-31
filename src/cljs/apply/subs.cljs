(ns apply.subs
  (:require [apply.prompts.subs]
            [apply.overview.subs]
            [apply.logistics.subs]
            [apply.personal.subs]
            [apply.community.subs]
            [apply.finish.subs]
            [re-frame.core :refer [reg-sub]]))

;; =============================================================================
;; App-wide
;; =============================================================================

(reg-sub
 :app/notifications
 (fn [db _]
   (get db :app/notifications)))

(reg-sub
 :app/complete?
 (fn [db _]
   (get db :app/complete)))

(reg-sub
 :app/initializing?
 (fn [db _]
   (get db :app/initializing)))
