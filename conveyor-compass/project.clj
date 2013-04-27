(defproject conveyor-compass "0.1.7"
  :description "Compass plugin for Conveyor"
  :url "https://github.com/mylesmegyesi/conveyor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [sass "3.2.6"]
                 [conveyor "0.1.7"]
                 [zweikopf "0.1.0"]]

  :profiles {:dev {:dependencies [[speclj "2.5.0"]
                                  [conveyor-sass "0.1.7"]]
                   :main speclj.main
                   :plugins [[speclj "2.5.0"]]
                   :test-paths ["spec"]}}

  :resource-paths ["gems"]

  )
