(ns conveyor.coffeescript-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer [find-asset]]
            [conveyor.coffeescript :refer :all])
  (:import [speclj SpecFailure]))

(describe "conveyor.coffeescript"

  (with base-dir (directory-path "test_fixtures/javascripts"))
  (with config (thread-pipeline-config
                 (configure-coffeescript)
                 (add-directory-to-load-path "test_fixtures/javascripts")))

  (defn test1-debug-output []
    (str
"var square;

square = function(x) {
  return x * x;
};
"))

  (it "compiles a coffeescript file"
    (let [found-asset (find-asset @config "test1.js")]
      (should (.contains (test1-debug-output) (:body found-asset)))))

  (it "reports sytax errors"
    (try
      (find-asset @config "syntax_error.js")
      (throw (SpecFailure. "I didn't throw"))
      (catch Exception e
        (should-contain "syntax_error.coffee: missing )" (.getMessage e)))))

  )

