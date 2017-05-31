(ns apply.db
  (:require [apply.prompts.models :refer [syncify]]
            [apply.prompts.db :as prompts]
            [apply.logistics.db :as logistics]
            [apply.personal.db :as personal]
            [apply.community.db :as community]
            [apply.finish.db :as finish]))

(def default-value
  (merge
   {:app/initializing  true
    :app/notifications []
    :app/properties    []
    :app/complete      nil}
   prompts/default-value
   (syncify (merge logistics/default-value
                   personal/default-value
                   community/default-value))
   finish/default-value))
