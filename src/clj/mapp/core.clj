(ns mapp.core
  (:gen-class)
  (:require [mapp.config]
            [mapp.server]
            [mapp.datomic]
            [mapp.countries]
            [mapp.nrepl]
            [mapp.log]
            [clojure.tools.cli :refer [parse-opts]]
            [mount.core :as mount]))


(def cli-options
  [["-e" "--environment ENVIRONMENT" "The environment to start the server in."
    :id :env
    :default :prod
    :parse-fn keyword
    :validate [#{:prod :dev :stage} "Must be one of #{:prod, :stage, :dev}"]]])

(defn- exit [status msg]
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options errors]} (parse-opts args cli-options)]
    (when errors
      (exit 1 (clojure.string/join "\n" errors)))
    (mount/start-with-args {:env (:env options)})))
