(ns starcity.components.tabs
  (:require [re-frame.core :refer [dispatch
                                   subscribe
                                   reg-sub
                                   reg-event-db]]
            [clojure.string :as s]))

;; =============================================================================
;; Internal
;; =============================================================================

(defn- tab-path
  [root-db-key new-name]
  (keyword (str (namespace root-db-key) "." (name root-db-key)) new-name))

;; =============================================================================
;; View

(defn- tab*
  [{:keys [key text]} root-db-key is-active]
  [:li {:class (when is-active "is-active")}
   [:a {:on-click #(dispatch [(tab-path root-db-key "goto-tab") key])}
    text]])

(defn tabs [root-db-key]
  (let [available-tabs (subscribe [(tab-path root-db-key "available-tabs")])
        active-tab     (subscribe [(tab-path root-db-key "active-tab")])]
    (fn [_]
      [:div.tabs.is-fullwidth
       [:ul
        (doall
         (for [{:keys [key] :as tab} @available-tabs]
           ^{:key key} [tab* tab root-db-key (= key @active-tab)]))]])))

;; =============================================================================
;; DB

(defn default-db
  [initial-tab & tabs]
  {::active-tab initial-tab ::available-tabs tabs})

(defn tab
  ([key]
   (tab key (-> (name key) s/capitalize)))
  ([key text]
   {:key key :text text}))

;; =============================================================================
;; Subs

(defn install-subscriptions
  [root-db-key]
  (do
    (reg-sub
     (tab-path root-db-key "active-tab")
     (fn [db _]
       (get-in db [root-db-key ::active-tab])))

    (reg-sub
     (tab-path root-db-key "available-tabs")
     (fn [db _]
       (get-in db [root-db-key ::available-tabs])))))

(defn subscribe-active-tab
  [root-db-key]
  (subscribe [(tab-path root-db-key "active-tab")]))

;; =============================================================================
;; Events

(defn install-events
  [root-db-key]
  (do
    (reg-event-db
     (tab-path root-db-key "goto-tab")
     (fn [db [_ key]]
       (assoc-in db [root-db-key ::active-tab] key)))))
