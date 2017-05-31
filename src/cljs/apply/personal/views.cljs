(ns apply.personal.views
  (:require [apply.prompts.views :as p]
            [apply.prompts.models :as prompts]
            [starcity.states :as states]
            [starcity.countries :refer [countries]]
            [starcity.dom :as dom]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [cljsjs.flatpickr]))

;; =============================================================================
;; Phone Number
;; =============================================================================

(defn- phone-number-content [phone-number]
  [:div.content
   [:p "We promise we'll keep your phone number private and only contact you
       by phone with prior permission."]
   [:div.form-container
    [:div.form-group
     [:label.label "Please enter your phone number below."]
     [:input#phone-number.input
      {:style       {:width "200px"}
       :placeholder "your phone number"
       :value       phone-number
       :type        "tel"
       :on-change   #(dispatch [:personal.phone-number/change (dom/val %)])}]]]])

;; =============================================================================
;; API

(defn phone-number []
  (let [phone-number (subscribe [:personal.phone-number/form-data])]
    (fn []
      (p/prompt
       (p/header "What is the best phone number to reach you at?")
       (p/content [phone-number-content @phone-number])))))

;; =============================================================================
;; Basic Info
;; =============================================================================

;; =============================================================================
;; Internal

(defn- consent-modal [info showing]
  [:div.modal {:class (when @showing "is-active")}
   [:div.modal-background]
   [:div.modal-container
    [:div.modal-content
     [:div.box
      [:div.content
       [:p.title.is-4 "Community Safety Check"]
       [:p "By checking this box, you are providing Starcity written instructions to procure a safety check on you to determine your eligibility for membership and rental capabilities in our community. This safety check is considered an investigative consumer report under California law, and will include a search for criminal records that may be linked to you. All safety checks will be produced in accordance with the Fair Credit Reporting Act and applicable state laws."]
       [:p "You have the right to inquire into whether or not a consumer report was in fact run on you, and, if so, to request the contact information of the consumer reporting agency who furnished the consumer report. You also have the right to view the information that a Consumer Reporting Agency holds on you. You may obtain a copy of this information in person at the Consumer Reporting Agency during regular business hours after providing proper identification and providing reasonable notice for your request. You are allowed to have one additional person accompany you so long as they provide proper identification. Additionally, you can make the same request via the contact information below. The Consumer Reporting Agency can assist you in understanding your file, including coded information."]

       [:ul.is-unstyled
        [:li [:strong "Community Safety"]]
        [:li [:strong "GoodHire, LLC"]]
        [:li "P.O. Box 391146"]
        [:li "Omaha, NE 68139"]
        [:li "855.278.7451"]
        [:li [:a {:href "mailto:support@communitysafety.goodhire.com"} "support@communitysafety.goodhire.com"]]]

       [:button.button.is-success
        {:on-click #(do (dispatch [:personal.background/consent true])
                        (reset! showing false))
         :style    {:margin-right "10px"}
         :type     "button"}
        "Agree"]
       [:button.button.is-danger {:on-click #(reset! showing false) :type "button"}
        '"Disagree"]]]]]
   [:button.modal-close {:on-click #(reset! showing false) :type "button"}]])

(defn- consent-group [_]
  (let [showing-modal (r/atom false)]
    (fn [{:keys [consent] :as info}]
      [:div.form-group
       [:label.label "First, do we have your consent to perform a background check?"]
       [:p.control
        [:label.checkbox
         [:input.checkbox {:type      "checkbox"
                           :checked   consent
                           :on-change (fn [evt]
                                        (if-not consent
                                          (do (.preventDefault evt)
                                              (reset! showing-modal (not @showing-modal)))
                                          (dispatch [:personal.background/consent false])))}]
         "Yes, I authorize Starcity to perform a background check on me."]]

       [consent-modal info showing-modal]])))

(defn- dob-group [_]
  (r/create-class
   {:display-name "dob-group"

    :component-did-mount
    (fn [this]
      (let [elt (js/document.getElementById "date-picker")
            fp  (js/window.Flatpickr.
                 elt
                 (clj->js {:altInput    true
                           :altFormat   "F j, Y"
                           :defaultDate (:dob (r/props this))
                           :onChange    #(dispatch [:personal.background/dob (.toISOString %)])}))]
        (r/set-state this {:flatpickr fp})))

    :component-did-update
    (fn [this _]
      (let [fp       (:flatpickr (r/state this))
            new-date (:dob (r/props this))]
        (when new-date
          (.setDate fp new-date))))

    :reagent-render
    (fn [_]
      [:div.form-group
       [:label.label "Great! We'll need to know your birthday."]
       [:div.date-container
        [:input#date-picker.input {:placeholder "choose a date"}]]])}))

(defn- name-group [{{:keys [first middle last]} :name}]
  (letfn [(-on-change [k]
            #(dispatch [:personal.background/name k (dom/val %)]))]
    [:div.form-group
     [:label.label "Our background check will be the most accurate with your full legal name."]
     [:div.control.columns
      ;; First name
      [:div.control.column
       [:input.input {:value first :on-change (-on-change :first)}]]
      ;; Middle name
      [:div.control.column
       [:input.input {:placeholder "middle name"
                      :value       middle
                      :on-change   (-on-change :middle)}]]
      ;; Last Name
      [:div.control.column
       [:input.input {:value last :on-change (-on-change :last)}]] ]]))

(defn- address-group [{:keys [address] :as info}]
  (letfn [(-on-change [k]
            #(dispatch [:personal.background/address k (dom/val %)]))]
    (let [{:keys [postal-code locality region country]} address]
      [:div.form-group
       [:label.label "Finally, we need to know some basic information about where you live."]
       [:div.control.columns
        ;; Country
        [:div.control.column
         [:span.select.is-fullwidth
          [:select {:value country :on-change (-on-change :country)}
           (for [{:keys [name code]} countries]
             ^{:key (str "country-" code)} [:option {:value code} name])]]]

        ;; City
        [:div.control.column
         [:input.input {:placeholder "City" :value locality :on-change (-on-change :locality)}]]

        ;; State
        [:div.control.column
         (if (= country "US")
           ;; US -- pick state from select
           [:span.select.is-fullwidth
            [:select {:value (or region "") :on-change (-on-change :region)}
             [:option {:disabled true :value ""} "Choose State"]
             (for [[abbr s] (sort-by val states/states-map)]
               ^{:key (str "state-" abbr)} [:option {:value abbr} s])]]

           ;; Non-US -- write-in
           [:input.input {:placeholder "State/Province"
                          :value       region
                          :on-change   (-on-change :region)}])]

        ;; Postal Code
        [:div.control.column
         [:input.input {:placeholder "Postal Code"
                        :value       postal-code
                        :on-change   (-on-change :postal-code)}]]]])))

(defn- background-check-content [{:keys [consent] :as info}]
  [:div.content
   [:p "We perform background checks to ensure the safety of our community members. Your background check is "
    [:strong "completely confidential"]
    ", and we'll share the results (if any) with you."]

   [:div.form-container
    [consent-group info]
    (when consent
      [:div
       [dob-group info]
       [name-group info]
       [address-group info]])]])

;; =============================================================================
;; API

(defn background-check-info []
  (let [info (subscribe [:personal.background/form-data])]
    (fn []
      (p/prompt
       (p/header "Help us keep our communities safe.")
       (p/content [background-check-content @info])))))

;; =============================================================================
;; Income Verification
;; =============================================================================

(defn- income-verification-content []
  (let [complete? (subscribe [:personal.income/complete?])]
    (fn []
      [:div.content
       [:p "We want to ensure that individuals interested in joining our
       communities are able to stay connected to other members without hiccups."]
       [:p "You can verify your income by providing your most recent pay stub,
       last year's W2, or your bank statements for the past three months."]
       [:div.form-container
        [:div.form-group
         [:label.label
          (if (prompts/complete? @complete?)
            "Your income files have been uploaded. Feel free to upload more if you'd like!"
            "Please upload your proof of income.")]
         [:input {:type      "file"
                  :multiple  true
                  :on-change #(dispatch [:personal.income/file-picked (.. % -currentTarget -files)])}]]]])))

(defn- income-verification-complete []
  [:div.content
   [:p "You're all set!"]])

;; =============================================================================
;; API

(defn income-verification []
  (p/prompt
   (p/header "Please verify your income.")
   (p/content [income-verification-content])))
