(defproject conveyor-closure "0.1.8"
  :description "Google Closure compiler plugin for conveyor"
  :url "https://github.com/mylesmegyesi/conveyor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [conveyor "0.1.7"]
                 [com.google.javascript/closure-compiler "r2388"]]

  :profiles {:dev {:dependencies [[speclj "2.6.0"]]
                   :main speclj.main
                   :plugins [[speclj "2.6.0"]]
                   :test-paths ["spec"]}}

  :scm {:name "git"
        :url "https://github.com/mylesmegyesi/conveyor"
        :dir "conveyor-closure"}
  )
