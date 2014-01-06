(ns conveyor.clojurescript-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.clojurescript :refer :all])
  (:import [java.io FileInputStream]))

(describe "conveyor.clojurescript"

  (with config (thread-pipeline-config
                 (add-directory-to-load-path "cljs")
                 (add-directory-to-load-path "other_cljs")
                 (assoc :plugins [:clojurescript])))

  (it "compiles a clojurescript file that has a dependecy somewhere else on the load path"
    (with-pipeline-config @config
      (let [{:keys [body]} (find-asset "example/hello.js")]
        (should (.contains body "alert")))))

  )
