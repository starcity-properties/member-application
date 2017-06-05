(ns mapp.server
  (:require [buddy.auth :as buddy]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.string :as string]
            [customs.access :as access]
            [mapp
             [config :as config :refer [config]]
             [datomic :refer [conn]]
             [routes :as routes]]
            [mount.core :refer [defstate]]
            [optimus
             [assets :as assets]
             [optimizations :as optimizations]
             [prime :as optimus]
             [strategies :as strategies]]
            [org.httpkit.server :as httpkit]
            [plumbing.core :as plumbing]
            [ring.middleware
             [content-type :refer [wrap-content-type]]
             [format :refer [wrap-restful-format]]
             [keyword-params :refer [wrap-keyword-params]]
             [multipart-params :refer [wrap-multipart-params]]
             [nested-params :refer [wrap-nested-params]]
             [not-modified :refer [wrap-not-modified]]
             [params :refer [wrap-params]]
             [resource :refer [wrap-resource]]
             [session :refer [wrap-session]]]
            [ring.middleware.session.datomic :refer [datomic-store session->entity]]
            [taoensso.timbre :as timbre]
            [ring.util.response :as response]))

(defn wrap-logging
  "Middleware to log requests."
  [handler]
  (fn [{:keys [uri request-method identity remote-addr] :as req}]
    (when-not (or (= uri "/favicon.ico")
                  (string/starts-with? uri "/assets")
                  (string/starts-with? uri "/bundles"))
      (timbre/info :web/request
                   (plumbing/assoc-when
                    {:uri         uri
                     :method      request-method
                     :remote-addr remote-addr}
                    :user (:account/email identity))))
    (handler req)))

(defn wrap-deps [handler deps]
  (fn [req]
    (handler (assoc req :deps deps))))

(def optimus-bundles
  {"apply.js"  ["/js/cljs/apply.js"]
   "apply.css" ["/assets/css/apply.css"]})

(defn- assemble-assets []
  (concat
   (assets/load-bundles "public" optimus-bundles)
   (assets/load-assets "public" [#"/assets/img/*"])))

(defn- unauthorized-handler
  "An unauthorized handler that redirects to the root domain's `login` endpoint
  when not in a development environment."
  [request metadata]
  (if-not (config/development? config)
    (response/redirect (format "%s/login" (config/root-domain config)))
    (let [[status body] (if (buddy/authenticated? request)
                          [403 "You are not authorized to view this page."]
                          [401 "You are not authenticated; please <a href='/login'>log in.</a>"])]
      (-> (response/response body)
          (response/status status)
          (response/content-type "text/html")))))

(def ^:private auth-backend
  (access/auth-backend :unauthorized-handler unauthorized-handler))

(defn app-handler [deps]
  (let [[optimize strategy] (if (config/development? config)
                              [optimizations/none strategies/serve-live-assets]
                              [optimizations/all strategies/serve-frozen-assets])]
    (-> routes/routes
        (optimus/wrap assemble-assets optimize strategy)
        (wrap-deps deps)
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend)
        (wrap-logging)
        (wrap-keyword-params)
        (wrap-nested-params)
        (wrap-restful-format)
        (wrap-params)
        (wrap-multipart-params)
        (wrap-resource "public")
        (wrap-session {:store        (datomic-store (:conn deps) :session->entity session->entity)
                       :cookie-name  (config/cookie-name config)
                       :cookie-attrs {:secure (config/secure-sessions? config)}})
        (wrap-content-type)
        (wrap-not-modified))))

(defn- start-server [port handler]
  (timbre/infof "webserver is starting on port %s" port)
  (httpkit/run-server handler {:port port :max-body (* 20 1024 1024)}))

(defn- stop-server [server]
  (timbre/info "webserver is shutting down")
  (server))

(defstate web-server
  :start (->> (app-handler {:conn conn})
              (start-server (config/webserver-port config)))
  :stop (stop-server web-server))
