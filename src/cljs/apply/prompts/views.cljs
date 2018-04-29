(ns apply.prompts.views
  (:require [apply.routes :refer [prompt-uri]]
            [apply.prompts.models :as prompts]
            [starcity.components.icons :as i]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [starcity.dom :as dom])
  (:refer-clojure :exclude [next]))

;; =============================================================================
;; Internal Components
;; =============================================================================

(defn- next-button [curr-prompt complete]
  (let [loading (subscribe [:prompt/is-loading])
        label   (subscribe [:prompt/next-button-label])]
    (fn [curr-prompt complete]
      [:button.next.is-medium.button.is-primary
       {:type     "submit"
        :disabled (not (prompts/locally-complete? complete))
        :class    (when @loading "is-loading")}
       [:span @label]
       (i/angle-right)])))

(defn- save-button [curr-prompt complete]
  (let [saving   (subscribe [:prompt/is-saving])
        can-save (subscribe [:prompt/can-save?])]
    (fn [curr-prompt complete]
      [:button.is-medium.button.is-info
       {:type     "button"
        :on-click #(dispatch [:prompt/save])
        :disabled (not @can-save)
        :class    (when @saving "is-loading")}
       [:span (if @can-save "Save" "Saved")]])))

(defn- previous-button [previous-prompt]
  [:a.button.is-medium.previous {:href (prompt-uri previous-prompt)}
   (i/angle-left)
   [:span "Back"]])

(defn- footer []
  (let [curr-prompt     (subscribe [:prompt/current])
        complete        (subscribe [(prompts/complete-key @curr-prompt)])
        previous-prompt (subscribe [:prompt/previous])]
    (fn []
      [:div.columns.is-mobile.prompt-controls
       (when @previous-prompt
         [:div.column.has-text-left [previous-button @previous-prompt]])
       [:div.column.has-text-right
        (when (prompts/complete? @complete)
          [:span {:style {:margin-right 8}} [save-button @curr-prompt @complete]])
        [next-button @curr-prompt @complete]]])))

(defn- advisor-image [hover]
  [:img.is-circular
   {:src   "/assets/img/meg.jpg"
    :alt   "community advisor headshot"
    :class (when hover "community-advisor")}])

;; =============================================================================
;; API
;; =============================================================================

(defn header
  [title]
  [:header
   [:h3.prompt-title.title.is-4 title]])

(defn content [content]
  [:div.prompt-content
   content])

(defn prompt [header content]
  [:div.prompt
   header
   [:form {:on-submit #(do
                         (.preventDefault %)
                         (dispatch [:prompt/next]))}
    content
    [footer]]])
