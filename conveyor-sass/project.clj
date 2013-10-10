(defproject conveyor-sass "0.2.1"
  :description "Sass plugin for conveyor"
  :url "https://github.com/mylesmegyesi/conveyor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [conveyor "0.2.1"]
                 [sass "3.2.6"]
                 [zweikopf "0.1.0"]]

  :profiles {:dev {:dependencies [[speclj "2.6.1"]]
                   :main speclj.main
                   :aot [speclj.main]
                   :plugins [[speclj "2.6.1"]]
                   :test-paths ["spec"]}}

  :resource-paths ["gems"]

  :scm {:name "git"
        :url "https://github.com/mylesmegyesi/conveyor"
        :dir "conveyor-sass"}

  )
