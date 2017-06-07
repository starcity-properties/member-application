(ns apply.finish.views
  (:require [apply.prompts.views :as p]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]))

(defn- referral []
  (let [sources (subscribe [:finish/referral-sources])
        chosen  (subscribe [:finish.referral/source])
        other   (r/atom false)]
    (fn []
      [:div.field.is-grouped
       [:p.control
        [:label.label "How did you hear about us?"]
        [:span.select
         [:select
          {:default-value @chosen
           :on-change     #(let [v (.. % -target -value)]
                             (reset! other (= v "other"))
                             (dispatch [:finish.referral/choose v]))}
          [:option {:disabled true :value "" :selected true} "please choose"]
          (doall
           (map-indexed
            #(with-meta [:option {:value %2} %2] {:key %1})
            @sources))]]]
       (when @other
         [:p.control.is-expanded
          [:label.label "Please elaborate."]
          [:input.input
           {:value       (if (= "other" @chosen) "" @chosen)
            :placeholder "e.g. airplane banner ad"
            :on-change   #(dispatch [:finish.referral/choose (.. % -target -value)])}]])])))

(defn- tos-privacy []
  (let [agreed (subscribe [:finish.pay.terms/agreed?])]
    [:div.field
     [:label.label "Have you read our Terms of Service and Privacy Policy?"]
     [:p.control
      [:label.checkbox
       [:input.checkbox {:type      "checkbox"
                         :checked   @agreed
                         :on-change #(dispatch [:finish.pay/toggle-agree])}]
       "Yes " [:span {:dangerouslySetInnerHTML {:__html "&mdash;"}}]
       " I have read and agree to both."]]]))

(defn- form []
  [:div.form-container
   [referral]
   [tos-privacy]])

(defn- payment-content []
  [:div.content
   [:p "Take a moment to review our "
    [:a {:href "https://joinstarcity.com/terms" :target "_blank"} "Terms of Service"]
    " and "
    [:a {:href "https://joinstarcity.com/privacy" :target "_blank"} "Privacy Policy"]
    "."]
   [:p "After you have read them and agreed using the checkbox below, you're
       ready to pay the $25 application fee by clicking the "
    [:strong "Finish & Pay"] " button below."]
   [form]])

(defn pay []
  (p/prompt
   (p/header "Great! Only one final step, and then your application is complete.")
   (p/content [payment-content])))
