(ns apply.community.models
  (:require [clojure.string :as s]))

(def ^:private long-enough 0)

;; NOTE: 20 is kind of arbitrary
(defn answer-long-enough? [s]
  (when-let [s s]
    (let [s' (s/replace s #"\s" "")]
      (> (count s') long-enough))))

(defn about-you-complete? [{:keys [free-time dealbreakers]}]
  (and (answer-long-enough? free-time)
       (answer-long-enough? dealbreakers)))

(defn communal-living-complete? [{:keys [prior-experience skills]}]
  (and (answer-long-enough? prior-experience)
       (answer-long-enough? skills)))
