(ns conveyor-clojurescript.core-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]
            [conveyor-clojurescript.core :refer :all]))

(describe "conveyor-clojurescript.core"

  (with config (thread-pipeline-config
                 configure-coffeescript
                 (add-directory-to-load-path "cljs")
                 (add-directory-to-load-path "other_cljs")))

  (it "compiles a clojurescript file"
    (with-pipeline-config @config
      (let [found-asset (find-asset "example/hello.js")]
        (should (.contains (:body found-asset) "alert")))))

  )
