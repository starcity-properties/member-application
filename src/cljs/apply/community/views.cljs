(ns apply.community.views
  (:require [apply.prompts.views :as p]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [starcity.dom :as dom]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Why Starcity
;; =============================================================================

(defn- internal-name->name [communities internal-name]
  (-> (filter #(= internal-name (:internal-name %)) communities)
      first
      :name))

(defn- names->phrase [names]
  (let [num (count names)]
    (case num
      0 "our communities"
      1 (first names)
      2 (str (first names) " and " (second names))
      (str (->> (interpose ", " (butlast names)) (apply str))
           ", and " (last names)))))

(defn- why-starcity-label []
  (let [selected  (subscribe [:logistics.communities/form-data])
        available (subscribe [:logistics/available-communities])]
    (fn []
      (let [names (map (partial internal-name->name @available) @selected)]
        [:label.label
         "Please tell the members of "
         (names->phrase names)
         " why you want to join their community."]))))

(defn why-starcity []
  (let [answer (subscribe [:community.why-starcity/form-data])]
    (fn []
      (p/prompt
       (p/header "Why do you want to live in a Starcity community?")
       (p/content
        [:div.content
         [:p "We believe that community is best created by the connections
         formed between individuals through shared values. Here at Starcity we
         empower our community members to cultivate and shape their communities
         by enabling members to determine which applicants they'd like to
         welcome into their homes."]
         [:p "The responses you provide within the " [:strong "Community Fitness"]
          " section will be made available to existing community members at the
          locations you applied to to aid in the selection of future members."]
         [:div.form-container
          [:div.field
           [why-starcity-label]
           [:textarea.textarea
            {:value     @answer
             :on-change #(dispatch [:community/why-starcity (dom/val %)])}]]]])))))

;; =============================================================================
;; About You
;; =============================================================================

(defn about-you []
  (let [answers (subscribe [:community.about-you/form-data])]
    (fn []
      (p/prompt
       (p/header "We'd like to get to know you better.")
       (p/content
        [:div.content
         [:p "Lasting relationships are often built through common interests.
         Help us get to know you by telling us more about yourself."]
         [:div.form-container
          [:div.field
           [:label.label "What do you like to do in your free time?"]
           [:textarea.textarea
            {:value     (:free-time @answers)
             :on-change #(dispatch [:community/about-you :free-time (dom/val %)])}]]
          [:div.field
           [:label.label "Do you have any dealbreakers?"]
           [:textarea.textarea
            {:value     (:dealbreakers @answers)
             :on-change #(dispatch [:community/about-you :dealbreakers (dom/val %)])}]]]])))))

;; =============================================================================
;; Communal Living
;; =============================================================================

(defn communal-living []
  (let [answers (subscribe [:community.communal-living/form-data])]
    (fn []
      (p/prompt
       (p/header "What excites you about living with others?")
       (p/content
        [:div.content
         [:p "Starcity is a safe space for individuals to come together and share their skills, experiences and perspectives. Our members give to one another and to the greater communities in which they live. We hope you’re as excited about sharing and giving back as we are."]
         [:div.form-container
          [:div.field
           [:label.label "Describe your past experience(s) living in shared spaces."]
           [:textarea.textarea
            {:value     (:prior-experience @answers)
             :on-change #(dispatch [:community/communal-living :prior-experience (dom/val %)])}]]
          [:div.field
           [:label.label "How will you contribute to the community?"]
           [:textarea.textarea
            {:value     (:skills @answers)
             :on-change #(dispatch [:community/communal-living :skills (dom/val %)])}]]
          [:div.field
           [:label.label "Please describe how you would resolve a conflict between yourself and another member of the home."]
           [:textarea.textarea
            {:value     (:conflicts @answers)
             :on-change #(dispatch [:community/communal-living :conflicts (dom/val %)])}]]]])))))
