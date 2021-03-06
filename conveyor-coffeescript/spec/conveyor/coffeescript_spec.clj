(ns conveyor.coffeescript-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.coffeescript :refer :all])
  (:import [java.lang AssertionError]))

(describe "conveyor.coffeescript"

  (with config (thread-pipeline-config
                 (assoc :plugins [:coffeescript])
                 (add-directory-to-load-path "test_fixtures/javascripts")))

  (around [it]
    (with-pipeline-config @config
      (it)))

  (defn test1-debug-output []
    (str
"(function() {
  var square;

  square = function(x) {
    return x * x;
  };

}).call(this);
"))

  (it "compiles a coffeescript file"
    (let [found-asset (find-asset "test1.js")]
      (should (.contains (test1-debug-output) (:body found-asset)))))

  (it "reports sytax errors"
    (try
      (find-asset "syntax_error.js")
      (throw (AssertionError. "I didn't throw"))
      (catch Exception e
        (should-contain "syntax_error.coffee: SyntaxError: missing )" (.getMessage e)))))

  )

