(ns conveyor.jst-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer [find-asset]]
            [conveyor.jst :refer :all]))

(describe "conveyor.jst"

  (with config (thread-pipeline-config
                 (configure-jst)
                 (add-directory-to-load-path "test_fixtures")))

  (defn test-output [path body]
    (format
      "(function() {this.JST || (this.JST = {}); this.JST[\"%s\"] = \"%s\"; }).call(this);"
      path
      body))

  (it "compiles a jst file"
    (let [found-asset (find-asset @config "test.js")]
      (should= (test-output "test" "<html>\\n  <body>\\n  </body>\\n</html>\\n")
               (:body found-asset))))

  (it "escapes"
    (let [found-asset (find-asset @config "quotes.js")]
      (should= (test-output "quotes" "<p>\\nThere are some's \\\"quotes\\\"\\n</p>\\n")
               (:body found-asset))))

  )
