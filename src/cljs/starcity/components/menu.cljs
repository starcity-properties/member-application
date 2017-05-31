(ns starcity.components.menu
  (:require [re-frame.core :refer [dispatch
                                   subscribe
                                   reg-sub
                                   reg-event-db]]
            [clojure.string :as s]))

;; =============================================================================
;; Internal
;; =============================================================================

(defn- path
  [root-key new-name]
  (keyword (str (namespace root-key) "." (name root-key)) new-name))

;; =============================================================================
;; View
