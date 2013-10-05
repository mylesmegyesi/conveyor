(defproject conveyor-clojurescript "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1859"
                  :exclusions [org.apache.ant/ant]]
                 [conveyor "0.2.0"]]
  :profiles {:dev
             {:dependencies [[speclj "2.7.4"]]
              :source-paths ["cljs", "other_cljs"]}}
  :plugins [[speclj "2.7.4"]]
  :test-paths ["spec"])
