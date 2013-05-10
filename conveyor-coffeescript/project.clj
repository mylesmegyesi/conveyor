(defproject conveyor-coffeescript "0.1.9"
  :description "Coffeescript plugin for conveyor"
  :url "https://github.com/mylesmegyesi/conveyor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [conveyor "0.1.9"]
                 [org.mozilla/rhino "1.7R4"]
                 [cheshire "5.1.1"]]

  :profiles {:dev {:dependencies [[speclj "2.6.1"]]
                   :main speclj.main
                   :plugins [[speclj "2.6.1"]]
                   :test-paths ["spec"]}}

  :scm {:name "git"
        :url "https://github.com/mylesmegyesi/conveyor"
        :dir "conveyor-coffeescript"}
  )
