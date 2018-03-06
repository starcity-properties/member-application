(ns starcity.components.service
  (:require [ant-ui.core :as a]
            [toolbelt.core :as tb]
            [cljs.spec.alpha :as s]))

(defn- price-range [prices]
  (str (apply min prices) "-" (apply max prices)))

(defn- price-text
  [{:keys [price billed selected fields variants] :as item}]
  (let [qty    (get selected :quantity 1)
        prices (remove nil? (conj (map :price variants) price))
        suf    (if (= billed :monthly) "/month" "")]
    (cond
      (empty? prices)      "Quote"
      (= (count prices) 1) (str "$" (* (first prices) qty) suf)
      :otherwise           (str "$" (price-range prices) suf))))

(defn- price
  [{:keys [rental selected] :as item} {:keys [on-delete]
                                       :or   {on-delete identity}}]
  [:span.tag
   {:class (if selected "is-success" "is-light")}
   (price-text item)
   (when selected
     [:button.delete.is-small
      {:type     :button
       :on-click (fn [_] (on-delete item))}])])

(defmulti form-field (fn [item field on-change] (:type field)))

(defmethod form-field :quantity
  [item {:keys [type key min max step]} on-change]
  [a/input-number
   {:min           min
    :max           max
    :step          step
    :default-value (get-in item [:selected :quantity])
    :autoFocus     true
    :on-change     #(on-change [(:service item) {key %}])}])

(defmethod form-field :desc
  [item {key :key} on-change]
  [a/input
   {:type          :textarea
    :autoFocus     true
    :default-value (get-in item [:selected :desc])
    :on-change     #(on-change [(:service item) {key (.. % -target -value)}])}])

(defn- variant-option [{:keys [id name price]}]
  [a/radio {:value id}
   (if price
     (str name " ($" price ")")
     name)])

(defmethod form-field :variants
  [item {key :key} on-change]
  [a/radio-group
   {:on-change     #(on-change [(:service item) {key (.. % -target -value)}])
    :default-value (get-in item [:selected :variant])}
   (map-indexed
    #(with-meta (variant-option %2) {:key %1})
    (:variants item))])

(defn- form-item
  [item field {:keys [on-change] :or {on-change identity}}]
  (let [{:keys [label type key min max step]} field]
    [:div.control
     [:label.label {:style {:color "#6f6f6f" :font-size "0.75rem"}} label]
     (form-field item field on-change)]))

(defn- form
  [{:keys [service selected fields] :as item} {:keys [on-select]
                                               :or          {on-select identity}
                                               :as          opts}]
  (cond
    (not selected)
    [:div [a/button {:type :ghost :on-click (fn [_] (on-select item))} "Select"]]

    (= 1 (count fields))
    [:div (form-item item (first fields) opts)]

    (> 1 (count fields))
    [:div.level
     [:div.level-left
      (map-indexed
       #(with-meta
          [:div.level-item (form-item item %2 opts)]
          {:key %1})
       fields)]]

    :else [:div]))

(defn card
  "Render a service as a card."
  [{:keys [name desc rental] :as item} opts]
  [a/card {:class "svc" :bodyStyle {:padding "10px 16px"}}
   [:div.level.is-mobile
    {:style {:margin-bottom "0.75em"}}
    [:div.level-left
     [:div.level-item
      [:h4.svc-title
       {:style                   {:font-weight 600 :margin-bottom 0}
        :dangerouslySetInnerHTML {:__html (str name (if rental "<i> (rental)</i>" ""))}}]]]
    [:div.level-right.has-text-right
     [:div.level-item
      (price item opts)]]]

   [:p.svc-desc
    {:style                   {:margin-bottom "0.75em"}
     :dangerouslySetInnerHTML {:__html desc}}]

   [:div.svc-foot
    [form item opts]]])
