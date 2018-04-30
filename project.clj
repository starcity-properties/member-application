(defproject mapp "1.3.0"
  :description "Starcity's Member Application"
  :url "http://apply.starcity.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/tools.reader "1.2.1"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/test.check "0.9.0"]
                 ;; Web
                 [ring/ring "1.6.3"]
                 [http-kit "2.2.0"]
                 [compojure "1.6.0"]
                 [optimus "0.19.1"]
                 [buddy "1.3.0"]
                 [ring-middleware-format "0.7.2"]
                 [optimus "0.19.1"]
                 [starcity/customs "1.0.0"
                  :exclusions [com.google.guava/guava
                               com.fasterxml.jackson.core/jackson-core]]
                 [starcity/datomic-session-store "0.1.0"]
                 [starcity/facade "0.4.0"]
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
                 [starcity/blueprints "2.5.0"
                  :exclusions [com.datomic/datomic-free
                               com.andrewmcveigh/cljs-time
                               com.google.guava/guava]]
                 [org.postgresql/postgresql "9.4.1211"]
                 [io.rkn/conformity "0.4.0"]
                 ;; Util
                 [starcity/teller "1.0.0"]
                 [aero "1.1.2"]
                 [bouncer "1.0.1"]
                 [cheshire "5.6.3"]
                 [clj-time "0.12.0"]
                 [mount "0.1.11"]
                 [com.taoensso/timbre "4.10.0"]
                 [starcity/toolbelt-async "0.4.0"]
                 [starcity/toolbelt-core "0.5.0"]
                 [starcity/toolbelt-date "0.3.0"]
                 [starcity/toolbelt-datomic "0.5.0"]
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

  :main mapp.core)
