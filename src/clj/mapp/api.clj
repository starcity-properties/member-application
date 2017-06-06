(ns mapp.api
  (:refer-clojure :exclude [update])
  (:require [blueprints.models
             [account :as account]
             [application :as application]
             [income-file :as income-file]
             [license :as license]]
            [bouncer
             [core :as b]
             [validators :as v :refer [defvalidator]]]
            [clj-time
             [coerce :as c]
             [core :as t]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET POST]]
            [datomic.api :as d]
            [mapp
             [config :as config :refer [config]]
             [countries :as countries]]
            [mapp.models.apply :as apply]
            [me.raynes.fs :as fs]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [toolbelt.validation :as validation]))

;; =============================================================================
;; Util
;; =============================================================================

(defn ->conn [req]
  (get-in req [:deps :conn]))

(def ->db (comp d/db ->conn))

(defn requester
  "Produce the `account` entity that initiated this `request`."
  [request]
  (let [id (get-in request [:identity :db/id])]
    (d/entity (->db request) id)))

(defn- json [response]
  (response/content-type response "application/json; charset=utf-8"))

(def ^:private ok (comp json response/response))

(defn- unprocessable [body]
  (-> (response/response body)
      (response/status 422)
      (json)))

(defn- malformed [body]
  (-> (response/response body)
      (response/status 400)
      (json)))

;; =============================================================================
;; Initialize
;; =============================================================================

(defn initialize-handler
  "Produce the requesting user's application progress."
  [req]
  (let [account (requester req)]
    (ok (apply/bootstrap (->db req) account))))

;; =============================================================================
;; Update
;; =============================================================================

;; =============================================================================
;; Validation

(defmulti ^:private validate (fn [_ _ k] k))

(defvalidator members
  {:default-message-format "Invalid community selection(s)."}
  [values coll]
  (every? #(coll %) values))

(defmethod validate :logistics/communities
  [db data _]
  (let [internal-names (d/q '[:find [?name ...] :where [_ :property/internal-name ?name]] db)]
    (b/validate
     data
     {:communities [[v/required :message "You must choose at least one community."]
                    [members (set internal-names)]]})))

(defmethod validate :logistics/license
  [db data _]
  (let [valid-ids (map :db/id (license/available db))]
    (b/validate
     data
     {:license [[v/required :message "You must choose a license."]
                [v/member (set valid-ids) :message "The chosen license is invalid."]]})))

(defmethod validate :logistics/move-in-date
  [_ data _]
  (b/validate
   data
   {:move-in-date [[v/required :message "You must supply a move-in-date."]
                   v/datetime]}))

(defmethod validate :logistics/pets
  [_ data _]
  (let [has-pet? :has-pet
        has-dog? (comp #{"dog"} :pet-type)]
    (b/validate
     data
     {:has-pet  [[v/required :message "Please let us know whether or not you have a pet."]
                 v/boolean]
      :pet-type [[v/required :message "Please let us know what type of pet you have." :pre has-pet?]
                 [v/member #{"cat" "dog"} :message "Your pet must be either a cat or a dog." :pre has-pet?]]
      :breed    [[v/required :message "Please let us know what kind of dog you have." :pre has-dog?]]
      :weight   [[v/required :message "Please let us know how much your dog weights." :pre has-dog?]
                 [v/integer :message "The weight must be an integer."]]})))

(defmethod validate :personal/phone-number
  [_ data _]
  (b/validate
   data
   {:phone-number [[v/required :message "You must supply a phone number."]]}))

(defvalidator over-eighteen?
  {:default-message-format "You must be at least 18 years old."}
  [date]
  (t/before? (c/to-date-time date) (t/minus (t/now) (t/years 18))))

(defmethod validate :personal/background
  [_ data _]
  (b/validate
   data
   {:consent [[v/boolean :message ":consent must be a boolean."] v/required]
    :dob     [[v/required :message "Your date-of-birth is required."] v/datetime over-eighteen?]
    :name    {:first [[v/required :message "Your first name is required."]]
              :last  [[v/required :message "Your last name is required."]]}
    :address {:country     [[v/required :message "The country that you presently live in is required."]
                            [v/member countries/codes :message "Please supply a valid country."]]
              :region      [[v/required :message "The state/province that you presently live in is required."]]
              :locality    [[v/required :message "The city/town that you live in is required."]]
              :postal-code [[v/required :message "Your postal code is required."]]}}))

(defmethod validate :community/why-starcity
  [_ data _]
  (b/validate
   data
   {:why-starcity [[v/required :message "Please tell us about why you want to join Starcity."]]}))

(defmethod validate :community/about-you
  [_ data _]
  (b/validate
   data
   {:free-time [[v/required :message "Please tell us about what you like to do in your free time."]]}))

(defmethod validate :community/communal-living
  [_ data _]
  (b/validate
   data
   {:prior-experience [[v/required :message "Please tell us about your experiences with communal living."]]
    :skills           [[v/required :message "Please tell us about your skills."]]
    :conflicts        [[v/required :message "Please tell us about how you resolve conflicts."]]}))

;; =============================================================================
;; Update

(def ^:private path->key
  (partial apply keyword))

(def ^:private submitted-msg
  "Your application has already been submitted, so it cannot be updated.")

(defn update-handler
  "Handle an update of user's application."
  [{:keys [params] :as req}]
  (let [account (requester req)
        app     (application/by-account (->db req) account)
        path    (path->key (:path params))
        vresult (validate (->db req) (:data params) path)]
    (cond
      ;; there's an application, and it's not in-progress
      (and app
           (not (application/in-progress? app))) (unprocessable {:errors [submitted-msg]})
      (not (validation/valid? vresult))          (malformed {:errors (validation/errors vresult)})
      :otherwise                                 (do
                                                   (apply/update (->conn req) (:data params) account path)
                                                   (ok (apply/progress (->db req) account))))))

;; =============================================================================
;; Income Files
;; =============================================================================

(defn- write-income-file!
  "Write a an income file to the filesystem and add an entity that points to the
  account and file path."
  [account {:keys [filename content-type tempfile size]}]
  (try
    (let [output-dir  (format "%s/income-uploads/%s" (config/data-dir config) (:db/id account))
          output-path (str output-dir "/" filename)]
      (do
        (when-not (fs/exists? output-dir)
          (fs/mkdirs output-dir))
        (io/copy tempfile (java.io.File. output-path))
        (income-file/create account content-type output-path size)))
    ;; catch to log, then rethrow
    (catch Exception e
      (timbre/error e "failed to write income file" {:user (account/email account)})
      (throw e))))

(defn create-income-files!
  "Save the income files for a given account."
  [conn account files]
  @(d/transact conn (mapv (partial write-income-file! account) files)))

(defn income-files-handler
  [{:keys [params] :as req}]
  (let [account (requester req)]
    (if-let [file-or-files (:files params)]
      (let [files (if (map? file-or-files) [file-or-files] file-or-files)]
        (create-income-files! (->conn req) account files)
        (ok (apply/progress (->db req) account)))
      (malformed {:errors ["You must choose at least one file to upload."]}))))

(defn payment-handler
  [{:keys [params] :as req}]
  (let [token   (:token params)
        account (requester req)
        app     (application/by-account (->db req) account)]
    (if (and token (apply/finished? (->db req) account))
      (do
        (apply/submit! (->conn req) token account)
        (ok {}))
      (malformed {:errors ["You must submit payment."]}))))

;; =============================================================================
;; Routes
;; =============================================================================

(defroutes routes
  (GET "/" [] initialize-handler)

  (POST "/update" [] update-handler)

  (POST "/verify-income" [] income-files-handler)

  (POST "/submit-payment" [] payment-handler))
