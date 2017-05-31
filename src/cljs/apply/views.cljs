(ns apply.views
  (:require [apply.menu.views :refer [menu]]
            [apply.overview.views :as overview]
            [apply.logistics.views :as logistics]
            [apply.personal.views :as personal]
            [apply.community.views :as community]
            [apply.complete.views :as complete]
            [apply.finish.views :as finish]
            [re-frame.core :refer [subscribe dispatch]]
            [starcity.components.notifications :as n]
            [starcity.components.loading :as l]))

;; =============================================================================
;; Components
;; =============================================================================

;; NOTE: Currently assuming they're all errors...
(defn- notification [idx {:keys [message type]}]
  (let [f (case type
            :success n/success
            :error   n/danger
            n/danger)]
    [f message #(dispatch [:notification/delete idx])]))

(defn- prompt []
  (let [current-prompt (subscribe [:prompt/current])]
    (fn []
      (case @current-prompt
        :overview/welcome          [overview/welcome]
        :logistics/communities     [logistics/choose-communities]
        :logistics/license         [logistics/choose-license]
        :logistics/move-in-date    [logistics/move-in-date]
        :logistics/pets            [logistics/pets]
        :personal/phone-number     [personal/phone-number]
        :personal/background       [personal/background-check-info]
        :personal/income           [personal/income-verification]
        :community/why-starcity    [community/why-starcity]
        :community/about-you       [community/about-you]
        :community/communal-living [community/communal-living]
        :finish/pay                [finish/pay]
        [:div.content (str "TODO: Implement view for " @current-prompt)]))))

(defn- prompts-view []
  (let [notifications (subscribe [:app/notifications])]
    (fn []
      [:div.columns
       [:div.column.is-one-quarter.is-hidden-mobile
        [menu]]
       [:div.column
        (doall
         (map-indexed
          (fn [idx n]
            ^{:key (str "notification-" idx)} [notification idx n])
          @notifications))
        [prompt]]])))

;; =============================================================================
;; API
;; =============================================================================

;; TODO: Use status, switch on the status
(defn app []
  (let [is-complete     (subscribe [:app/complete?])
        is-initializing (subscribe [:app/initializing?])]
    (fn []
      [:div.container
       (case @is-complete
         true [complete/complete]
         false [prompts-view]
         (l/fill-container))])))
