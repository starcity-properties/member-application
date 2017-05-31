(ns apply.core
  (:require [apply.views :refer [app]]
            [apply.routes :as routes]
            [apply.db]
            [apply.subs]
            [apply.events]
            [apply.fx]
            [re-frame.core :refer [dispatch-sync]]
            [reagent.core :as reagent]
            [clojure.spec.test :as stest]))

;; =============================================================================
;; Config
;; =============================================================================

(enable-console-print!)

;; =============================================================================
;; API
;; =============================================================================

(defn ^:export run []
  (routes/app-routes)
  (dispatch-sync [:app/initialize])
  (reagent/render [app] (.getElementById js/document "apply")))
