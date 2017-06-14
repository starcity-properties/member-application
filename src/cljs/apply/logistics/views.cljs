(ns apply.logistics.views
  (:require [apply.prompts.views :as p]
            [apply.routes :refer [prompt-uri]]
            [starcity.dom :as dom]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [cljsjs.flatpickr]
            [starcity.log :as l]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Choose Communities
;; =============================================================================

;; =============================================================================
;; Internal

(defn- handle-select-community
  [community-name e]
  (dispatch [:logistics.communities/select community-name (dom/checked e)]))

(def ^:private community-links
  {"52gilbert"   "https://joinstarcity.com/communities/soma"
   "2072mission" "https://joinstarcity.com/communities/mission"})

(defn- community [{:keys [internal-name name num-units available-on]} selections]
  [:p.control
   [:label.checkbox
    [:input {:type            "checkbox"
             :default-checked (selections internal-name)
             :on-click        (partial handle-select-community internal-name)}]
    (if-let [uri (get community-links internal-name)]
      [:a {:href uri :target "_blank"} name]
      [:strong name])
    [:span.num-units (str num-units " units open")]
    [:span.availability (str "available " available-on)]]])

;; =============================================================================
;; API

(defn choose-communities []
  (let [selections  (subscribe [:logistics.communities/form-data])
        communities (subscribe [:logistics/available-communities])]
    (fn []
      (p/prompt
       (p/header "Which Starcity communities would you like to join?")
       (p/content
        [:div.content
         [:p "With communities located throughout San Francisco, we're sure you'll find one that complements your lifestyle."]

         [:div.form-container
          [:label.label "Select the communities you're interested in calling home."]
          (doall
           (for [c @communities]
             ^{:key (:internal-name c)} [community c @selections]))]])))))

;; =============================================================================
;; Duration of Stay
;; =============================================================================

;; =============================================================================
;; Internal

(defn- label-for [term]
  (cond
    (= term 1)        "Month-to-month"
    (< (/ term 12) 1) (str term " months")
    :otherwise        (str (/ term 12) " year")))

(defn- license-form [selected-license licenses]
  [:div.form-container
   [:label.label "Choose the option that works best for you."]
   [:p.control
    (doall
     (for [{:keys [id term]} licenses]
       ^{:key id}
       [:label.radio
        [:input {:type            "radio"
                 :name            "license"
                 :default-checked (= id selected-license)
                 :value           id
                 :on-change       #(dispatch [:logistics.license/select (-> % dom/val js/parseInt)])}]
        (label-for term)]))]])

(defn- format-price [price]
  (str "$" price "/mo"))

(defn- table-head [selected-license licenses]
  [:thead
   [:tr
    [:th "Community"]
    (for [{:keys [id term]} licenses]
      ^{:key (str "th-" id)}
      [:th {:class (when (= selected-license id) "is-active")}
       (label-for term)])]])

(defn- term-row
  [selected-communities selected-license {:keys [name internal-name prices]}]
  (let [is-selected-community? (selected-communities internal-name)]
    [:tr
     [:td {:class (when is-selected-community? "is-active")} name]
     (for [{:keys [price license-id]} prices]
       ^{:key license-id}
       [:td {:class (when (and is-selected-community?
                               (= selected-license license-id))
                      "is-active")}
        (format-price price)])]))

(defn- terms-table [_ _]
  (let [license-prices       (subscribe [:logistics.license/license-prices])
        selected-communities (subscribe [:logistics.communities/form-data])]
    (fn [selected-license licenses]
      [:table.table.is-narrow
       [table-head selected-license licenses]
       [:tbody
        (doall
         (for [row-data @license-prices]
           ^{:key (str "row-" (:internal-name row-data))}
           [term-row @selected-communities selected-license row-data]))]])))


(defn- choose-license-content [selected-license]
  (let [licenses (subscribe [:logistics.license/available-licenses])]
    (fn [selected-license]
      [:div.content
       [:p "We understand that each individual has unique housing needs, which is why we offer three membership plans ranging from " [:strong "most affordable"] " to " [:strong "most flexible"] "."]

       [:p "Our rates vary from community to community "
        [:span {:dangerouslySetInnerHTML {:__html "&mdash;"}}]
        " here's a breakdown:"]

       [terms-table selected-license @licenses]

       [license-form selected-license @licenses]])))

;; =============================================================================
;; API

(defn choose-license []
  (let [selected-license (subscribe [:logistics.license/form-data])]
    (fn []
      (p/prompt
       (p/header "How long would you like to be part of the Starcity community?")
       (p/content [choose-license-content @selected-license])))))

;; =============================================================================
;; Move-in Date
;; =============================================================================

;; =============================================================================
;; Internal

(defn- move-in-date-content [data]
  (let [min-date (c/to-date (t/plus (t/now) (t/weeks 1)))]
    (r/create-class
     {:display-name "move-in-date-content"
      :component-did-mount
      (fn [this]
        (let [elt (js/document.getElementById "date-picker")
              fp  (js/window.Flatpickr.
                   elt
                   (clj->js {:altInput    true
                             :altFormat   "F j, Y"
                             :minDate     min-date
                             :defaultDate (:data (r/props this))
                             :onChange    #(dispatch [:logistics.move-in-date/choose
                                                      (.toISOString %)])}))]
          (r/set-state this {:flatpickr fp})))

      :component-did-update
      (fn [this _]
        (let [fp       (:flatpickr (r/state this))
              new-date (:data (r/props this))]
          (.setDate fp new-date)))

      :reagent-render
      (fn [_]
        [:div.content
         [:p "We'll be there for you on move-in day to help you get your new room feeling like home."]
         [:p [:strong "Disclaimer: "] "While we hope to be able to accommodate your preferred move-in date, we cannot guarantee that the date you choose will be the date that you move in."]
         [:div.form-container
          [:label.label "Choose your ideal move-in date."]
          [:div.date-container
           [:input#date-picker.input
            {:placeholder "click here"}]]]])})))

;; =============================================================================
;; API

(defn move-in-date []
  (let [move-in-date (subscribe [:logistics.move-in-date/form-data])]
    (fn []
      (p/prompt
       (p/header "When would you like to move in?")
       ;; NOTE: If we want to access props in a lifecycle method with
       ;; `reagent.core/props`, props need to be a map!
       (p/content [move-in-date-content {:data @move-in-date}])))))

;; =============================================================================
;; Pets
;; =============================================================================

;; =============================================================================
;; Internal


(defn binary-radio
  [{:keys [name value on-change] :as opts}]
  (let [to-bool #(= % "yes")]
    [:p.control
     (doall
      (for [[lbl val] [["Yes" "yes"] ["No" "no"]]]
        ^{:key lbl}
        [:label.radio
         [:input {:type      "radio"
                  :name      name
                  :checked   (= value (to-bool val))
                  :value     val
                  :on-change #(on-change (-> % dom/val to-bool))}]
         lbl]))]))


(defn field
  [{:keys [is-grouped] :as opts} & children]
  [:div.field {:class (when is-grouped "is-grouped")}
   (map-indexed #(with-meta %2 {:key %1}) children)])


(defn control
  [{:keys [label is-expanded] :or {is-expanded true} :as opts} input]
  [:div.control {:class (when is-expanded "is-expanded")}
   (when label [:label.label label])
   input])


(defn- on-change
  ([k]
   (on-change k identity))
  ([k tf]
   #(dispatch [:logistics.pets/update! k (tf %)])))


(defn- binary-radio* [info k]
  [binary-radio {:name (name k) :value (get info k) :on-change (on-change k)}])


(defn- breed-weight
  [{:keys [breed weight] :as info}]
  [field {:is-grouped true}
   [control {:is-expanded true
             :label       "What breed?"}
    [:input.input
     {:placeholder "breed"
      :value       breed
      :on-change   (on-change :breed dom/val)}]]
   [control {:is-expanded true
             :label       "How much does he/she weigh?"}
    [:div.field.has-addons.has-addons-right
     [:p.control.is-expanded
      [:input.input
       {:type        "number"
        :placeholder "weight"
        :value       weight
        :on-change   (on-change :weight (comp js/parseInt dom/val))}]]
     [:a.button {:disabled true} "lbs"]]]])


(defn- pet-radios [info]
  [field {:is-grouped true}
   [control {:label       "Is your pet spayed/neutered?"
             :is-expanded true}
    [binary-radio* info :sterile]]
   [control {:label       "Are your pet's vaccines and/or licenses current?"
             :is-expanded true}
    [binary-radio* info :vaccines]]
   [control {:label       "Has your pet ever bitten a human?"
             :is-expanded true}
    [binary-radio* info :bitten]]])


(defn- pets-content
  [{:keys [daytime-care demeanor] :as info}]
  (let [dog (= "dog" (:pet-type info))]
    [:div.content
     [:p "Most of our communities are dog-friendly, but we unfortunately do not allow cats. If you have a dog, please let us know what breed and weight."]
     [:div.form-container
      [field {}
       [control {:label "Do you have a dog?"}
        [binary-radio* info :has-pet]]]
      (when dog
        [:div
         [breed-weight info]
         [field {}
          [control {:label "How will your pet be taken care of during the day?"}
           [:input.input
            {:type      "text"
             :value     daytime-care
             :on-change (on-change :daytime-care dom/val)}]]]
         [field {}
          [control {:label "Please describe your pet's demeanor."}
           [:textarea.textarea
            {:value     demeanor
             :on-change (on-change :demeanor dom/val)}]]]
         [pet-radios info]])]]))


;; =============================================================================
;; API
;; =============================================================================


(defn pets []
  (let [pets-info (subscribe [:logistics.pets/form-data])]
    (p/prompt
     (p/header "Do you have a furry friend who will be living with you?")
     (p/content [pets-content @pets-info]))))
