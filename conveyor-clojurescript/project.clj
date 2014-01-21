(defproject conveyor-clojurescript "0.2.8"
  :description "Clojurescript plugin for conveyor"
  :url         "https://github.com/mylesmegyesi/conveyor"
  :license     {:name "Eclipse Public License"
                :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138" :exclusions [org.apache.ant/ant]]
                 [conveyor "0.2.8"]]

  :profiles {:dev {:dependencies [[speclj "2.9.1"]]
                   :main         speclj.main
                   :plugins      [[speclj "2.9.1"]]
                   :target-path  "target/"
                   :test-paths   ["spec"]
                   :uberjar-name "conveyor-clojurescript-standalone.jar"}
             :deploy {}}

  :scm {:name "git"
        :url "https://github.com/mylesmegyesi/conveyor"
        :dir "conveyor-clojurescript"}

  )
