(ns apply.prompts.interceptors
  (:require [re-frame.core :refer [->interceptor]]))

(def is-editing
  "Indicates that this event transitions the app into editing mode...can't I do
  this by just checking if :remote and :local are =? ANSWER: YES

  NOT USING THIS:"
  (->interceptor
   :id :is-editing
   :after (fn [ctx]
            (let [db (get-in ctx [:effects :db])]
              (assoc-in ctx [:effects :db (:prompt/current db) :is-editing?] true)))))
