(ns mapp.routes
  (:require [buddy.auth.accessrules :refer [restrict]]
            [compojure.core :as compojure :refer [context defroutes GET POST]]
            [customs
             [access :as access]
             [auth :as auth]
             [role :as role]]
            [datomic.api :as d]
            [facade
             [core :as facade]
             [snippets :as snippets]]
            [mapp
             [api :as api]
             [config :as config :refer [config]]]
            [mapp.models.apply :as apply]
            [net.cgrand.enlive-html :as html]
            [ring.util.response :as response]
            [clojure.java.io :as io]))

(defn- login! [{:keys [params session deps] :as req}]
  (let [{:keys [email password]} params
        account (auth/authenticate (d/db (:conn deps)) email password)]
    (if (:account/activated account)
      (let [session (assoc session :identity account)]
        (-> (response/redirect "/")
            (assoc :session session)))
      (-> (response/response "Invalid credentials")
          (response/status 400)))))

(html/defsnippet apply-content "templates/apply.html" [:section] []
  [:section] (html/append (snippets/loading-fullscreen)))

(defn- show-apply [req]
  (let [render (partial apply str)]
    (-> (facade/app req "apply"
                    :content (apply-content)
                    :navbar (snippets/app-navbar
                             :logout-href (str (config/root-domain config) "/logout"))
                    :chatlio? true
                    :scripts ["https://checkout.stripe.com/checkout.js"]
                    :json [["stripe" {:amount apply/application-fee
                                      :key    (config/stripe-public-key config)}]]
                    :css-bundles ["apply.css"]
                    :stylesheets [facade/font-awesome])
        (render)
        (response/response)
        (response/content-type "text/html"))))

(defroutes routes
  (GET "/login" []
       (fn [req]
         (if-not (config/development? config)
           (response/redirect (format "%s/login" (config/root-domain config)))
           (-> (response/resource-response "public/login.html")
               (response/content-type "text/html")))))

  (POST "/login" [] login!)

  (context "/api" []
    (restrict api/routes
      {:handler {:and [access/authenticated-user (access/user-isa role/applicant)]}}))

  (context "/" []
    (restrict (compojure/routes (GET "*" [] show-apply))
      {:handler {:and [access/authenticated-user (access/user-isa role/applicant)]}})))
