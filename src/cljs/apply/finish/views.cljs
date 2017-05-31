(ns apply.finish.views
  (:require [apply.prompts.views :as p]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]))

(defn- payment-content []
  (let [agreed (subscribe [:finish.pay.terms/agreed?])]
    (fn []
      [:div.content
       [:p "Take a moment to review our "
        [:a {:href "https://joinstarcity.com/terms" :target "_blank"} "Terms of Service"]
        " and "
        [:a {:href "https://joinstarcity.com/privacy" :target "_blank"} "Privacy Policy"]
        "."]
       [:p "After you have read them and agreed using the checkbox below, you're
       ready to pay the $25 application fee by clicking the "
        [:strong "Finish & Pay"] " button below."]
       [:div.form-container
        [:div.form-group
         [:label.label "Have you read our Terms of Service and Privacy Policy?"]
         [:p.control
          [:label.checkbox
           [:input.checkbox {:type      "checkbox"
                             :checked   @agreed
                             :on-change #(dispatch [:finish.pay/toggle-agree])}]
           "Yes " [:span {:dangerouslySetInnerHTML {:__html "&mdash;"}}]
           " I have read and agree to both."]]]]])))

(defn pay []
  (p/prompt
   (p/header "Great! Only one final step, and then your application is complete.")
   (p/content [payment-content])))
