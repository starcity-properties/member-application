(ns apply.menu.views
  (:require [apply.routes :refer [prompt-uri]]
            [apply.prompts.models :as prompts]
            [re-frame.core :refer [subscribe]]
            [reagent.core :as r]))

;; =============================================================================
;; Components
;; =============================================================================

(def ^:private prompt-labels
  {:overview/welcome "Welcome!"

   :logistics/communities  "Communities"
   :logistics/license      "Duration of Stay"
   :logistics/move-in-date "Move-in Date"
   :logistics/pets         "Pets"

   :personal/phone-number "Phone Number"
   :personal/background   "Background Check"
   :personal/income       "Income Verification"

   :community/why-starcity    "Why Starcity?"
   :community/about-you       "About You"
   :community/communal-living "Communal Living"
   :finish/pay                "Payment"})

(defn- menu-label
  ([text]
   [:p.menu-label text])
  ([text icon]
   [:p.menu-label
    [:span.icon.is-small
     [:i.fa {:class (name icon)}]]
    text]))

(defn- menu-item* [curr-prompt this-prompt disabled]
  (let [complete (subscribe [(prompts/complete-key this-prompt)])]
    (fn [curr-prompt this-prompt disabled]
      [:li
       [:a {:class (str (when (= this-prompt curr-prompt) "is-active")
                        (when disabled " is-disabled")
                        (when (prompts/complete? @complete) " is-complete"))
            :href  (when-not disabled (prompt-uri this-prompt))}
        (get prompt-labels this-prompt)
        (when (prompts/complete? @complete)
          [:span.is-pulled-right.icon.is-small [:i.fa.fa-check]])]])))

(defn- menu-list [& prompts]
  (let [curr-prompt      (subscribe [:prompt/current])
        payment-allowed? (subscribe [:prompt/payment-allowed?])]
    (fn [& prompts]
      [:ul.menu-list
       (doall
        (for [p prompts]
          ^{:key (str "menu-list-item-" p)} [menu-item*
                                             @curr-prompt
                                             p
                                             (and (not @payment-allowed?) (= p :finish/pay))]))])))


;; =============================================================================
;; API
;; =============================================================================

(defn menu []
  [:aside.menu.prompt-menu
   (menu-label "Overview")
   [menu-list
    :overview/welcome]
   (menu-label "Logistics")
   [menu-list
    :logistics/communities
    :logistics/license
    :logistics/move-in-date
    :logistics/pets]
   (menu-label "Personal Information")
   [menu-list
    :personal/phone-number
    :personal/background
    :personal/income]
   (menu-label "Community Fitness")
   [menu-list
    :community/why-starcity
    :community/about-you
    :community/communal-living]
   (menu-label "Finish")
   [menu-list
    :finish/pay]])
