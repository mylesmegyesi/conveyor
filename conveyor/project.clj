(defproject conveyor "0.1.4"
  :description "An implementation of the Rails asset pipeline for Clojure"
  :url "https://github.com/mylesmegyesi/conveyor"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [digest "1.4.3"]
                 [org.apache.commons/commons-io "1.3.2"]
                 [com.novemberain/pantomime "1.7.0"]]

  :profiles {:dev {:dependencies [[speclj "2.5.0"]
                                  [ring-mock "0.1.3"]]
                   :main speclj.main
                   :resource-paths ["test_fixtures/resources"]
                   :plugins [[speclj "2.5.0"]]
                   :test-paths ["spec"]}}
  )
