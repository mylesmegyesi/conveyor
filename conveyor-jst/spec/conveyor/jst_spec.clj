(ns conveyor.jst-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer [find-asset with-pipeline-config]]
            [conveyor.jst :refer :all]))

(describe "conveyor.jst"

  (with config (thread-pipeline-config
                 (configure-jst)
                 (add-directory-to-load-path "test_fixtures")))

  (around [it]
    (with-pipeline-config @config
      (it)))

  (defn test-output [path body]
    (format
      "(function() {this.JST || (this.JST = {}); this.JST[\"%s\"] = \"%s\"; }).call(this);"
      path
      body))

  (it "compiles a jst file"
    (let [found-asset (find-asset "test.js")]
      (should= (test-output "test.js" "<html>\\n  <body>\\n  </body>\\n</html>\\n")
               (:body found-asset))))

  (it "escapes"
    (let [found-asset (find-asset "quotes.js")]
      (should= (test-output "quotes.js" "<p>\\nThere are some's \\\"quotes\\\"\\n</p>\\n")
               (:body found-asset))))

  )
