(ns apply.complete.events
  (:require [re-frame.core :refer [reg-event-db]]))

(reg-event-db
 :complete/nav
 (fn [db _]
   (assoc db :app/complete true)))
