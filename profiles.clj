{:dev {:source-paths ["src/clj" "src/cljs" "env/dev"]
       :plugins      [[lein-figwheel "0.5.11" :exclusions [org.clojure/clojure org.clojure/core.async]]
                      [lein-cooper "1.2.2" :exclusions [org.clojure/clojure]]]
       :dependencies [[figwheel-sidecar "0.5.11"]
                      [starcity/reactor "1.9.0-SNAPSHOT"]
                      [binaryage/devtools "0.9.2"]]
       :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

       :cooper {"styles" ["sass" "--watch" "-E" "UTF-8" "style/main.sass:resources/public/assets/css/apply.css"]}}

 :uberjar {:aot          :all
           :main         mapp.core
           :source-paths ["src/clj" "src/cljs"]
           :prep-tasks   ["compile" ["cljsbuild" "once"]]
           :cljsbuild
           {:builds [{:id           "apply"
                      :source-paths ["src/cljs/apply" "src/cljs/starcity"]
                      :jar          true
                      :compiler     {:main             apply.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     false
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/apply/out"
                                     :output-dir       "resources/public/js/cljs/apply/out"
                                     :output-to        "resources/public/js/cljs/apply.js"
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}]}}}
