(defproject conveyor-compass "0.2.8"
  :description "Compass plugin for Conveyor"
  :url         "https://github.com/mylesmegyesi/conveyor"
  :license     {:name "Eclipse Public License"
                :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [sass "3.2.6"]
                 [conveyor "0.2.8"]
                 [zweikopf "0.1.0"]]

  :profiles {:dev {:dependencies [[speclj "2.9.1"]
                                  [conveyor-sass "0.2.8"]]
                   :main speclj.main
                   :plugins [[speclj "2.9.1"]]
                   :target-path  "target/"
                   :test-paths ["spec"]
                   :uberjar-name "conveyor-compass-standalone.jar"}
             :deploy {}}

  :resource-paths ["gems"]

  :scm {:name "git"
        :url "https://github.com/mylesmegyesi/conveyor"
        :dir "conveyor-compass"}

  )
