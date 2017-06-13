(defproject mapp "1.1.0-SNAPSHOT"
  :description "Starcity's Member Application"
  :url "http://apply.joinstarcity.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/test.check "0.9.0"]
                 ;; Web
                 [ring/ring "1.5.0"]
                 [http-kit "2.2.0"]
                 [compojure "1.5.1"]
                 [optimus "0.19.1"]
                 [buddy "1.3.0"]
                 [ring-middleware-format "0.7.2"]
                 [optimus "0.19.1"]
                 [starcity/customs "0.1.0"]
                 [starcity/datomic-session-store "0.1.0"]
                 [starcity/facade "0.1.1"]
                 ;; CLJS
                 [cljsjs/flatpickr "2.0.0-rc.7-0"]
                 [reagent "0.6.0"]
                 [re-frame "0.9.2" :exclusions [reagent]]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.7"]
                 [day8.re-frame/http-fx "0.1.2"]
                 [starcity/ant-ui "0.1.2" :exclusions [re-frame]]
                 [starcity.re-frame/chatlio-fx "0.1.0"]
                 [starcity.re-frame/stripe-fx "0.1.0"]
                 [cljsjs/react "15.4.2-2"]
                 [cljsjs/react-dom "15.4.2-2"]
                 ;; DB
                 [com.datomic/datomic-pro "0.9.5544" :exclusions [com.google.guava/guava]]
                 [starcity/blueprints "1.6.1" :exclusions [com.datomic/datomic-free]]
                 [org.postgresql/postgresql "9.4.1211"]
                 [io.rkn/conformity "0.4.0"]
                 ;; Util
                 [aero "1.1.2"]
                 [bouncer "1.0.1"]
                 [cheshire "5.6.3"]
                 [clj-time "0.12.0"]
                 [mount "0.1.11"]
                 [com.taoensso/timbre "4.10.0"]
                 [prismatic/plumbing "0.5.3"]
                 [starcity/ribbon "0.1.0"]
                 [starcity/toolbelt "0.1.5" :exclusions [com.datomic/datomic-free]]
                 [me.raynes/fs "1.4.6"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [s3-wagon-private "1.2.0"]]

  :jar-name "mapp.jar"

  :jvm-opts ["-server"
             "-Xmx4g"
             "-XX:+UseCompressedOops"
             "-XX:+DoEscapeAnalysis"
             "-XX:+UseConcMarkSweepGC"]

  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username :env/datomic_username
                                   :password :env/datomic_password}

                 "releases" {:url        "s3://starjars/releases"
                             :username   :env/aws_access_key
                             :passphrase :env/aws_secret_key}}

  :repl-options {:init-ns user}

  :clean-targets ^{:protect false} ["resources/public/js/cljs" :target-path]

  :main mapp.core
  )
