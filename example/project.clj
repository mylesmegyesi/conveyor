(defproject example "1.0.0"
  :description "An example app using conveyor"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [conveyor "0.2.8"]
                 [hiccup "1.0.4"]
                 [compojure "1.1.6"]]

  :source-paths ["src/clj"]
  :test-paths ["spec/clj"]

  :plugins [[lein-ring "0.8.8" :exclusions [org.clojure/clojure]]]

  :ring {:handler example.core/handler
         :port 8080
         :stacktraces? true
         :auto-reload? true
         :reload-paths ["src"]
         :auto-refresh? false
         :nrepl {:start? false}}

  :profiles {:dev {:dependencies [[speclj "2.9.1"]
                                  [ring-mock "0.1.5"]

                                  ; assets
                                  [conveyor-sass "0.2.8"]
                                  [conveyor-clojurescript "0.2.8" :exclusions [org.clojure/clojurescript]]

                                  ; cljs deps
                                  [specljs "2.9.1"]
                                  [org.clojure/clojurescript "0.0-2014"]]

                   :plugins [[speclj "2.9.1"]
                             [lein-cljsbuild "1.0.1"]]
                   :cljsbuild ~(let [test-command ["bin/specljs" "target/specs.js"]]
                                {:builds {:dev {:source-paths ["src/cljs" "spec/cljs"]
                                                :compiler {:output-to "target/specs.js"
                                                :output-dir "target/cljs-spec"
                                                :optimizations :advanced
                                                :pretty-print false}
                                                :notify-command test-command}}})}}
)
