(ns conveyor.sass-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.sass :refer :all]))

(describe "conveyor.sass"

  (with stylesheets1 (directory-path "test_fixtures/stylesheets1"))
  (with config (thread-pipeline-config
                 (configure-sass)
                 (add-directory-to-load-path "test_fixtures/stylesheets1")))

  (with test4-url (asset-url "test4.png"))

  (around [it]
    (with-pipeline-config @config
      (it)))

  (defn test1-debug-output []
    (format
"/* on line 4 of %s/test1.scss */
.content-navigation {
  border-color: #3bbfce;
  color: #2ca2af;
}

/* on line 10 of %s/test1.scss */
.border {
  padding: 8px;
  margin: 8px;
  border-color: #3bbfce;
}
"
      @stylesheets1
      @stylesheets1)
    )

  (defn test1-compressed-output []
".content-navigation{border-color:#3bbfce;color:#2ca2af}.border{padding:8px;margin:8px;border-color:#3bbfce}
")

  (defn test2-debug-output []
    (format
"/* on line 4 of %s/test2.sass */
.content-navigation {
  border-color: #3bbfce;
  color: #2ca2af;
}

/* on line 8 of %s/test2.sass */
.border {
  padding: 8px;
  margin: 8px;
  border-color: #3bbfce;
}
"
      @stylesheets1
      @stylesheets1)
    )

  (defn test3-debug-output []
    (format
"/* on line 1 of %s/test3.scss */
.test-asset-path {
  background-image: url(\"%s\");
}

/* on line 5 of %s/test3.scss */
.test-asset-url {
  background-image: url(\"%s\");
}
"
      @stylesheets1
      @test4-url
      @stylesheets1
      @test4-url))

  (it "compiles a scss file"
    (let [found-asset (find-asset "test1.css")]
      (should (.contains (test1-debug-output) (:body found-asset)))))

  (it "compresses a scss file"
    (with-pipeline-config (set-compression @config true)
      (let [found-asset (find-asset "test1.css")]
        (should (.contains (test1-compressed-output) (:body found-asset))))))

  (it "compiles a sass file"
    (let [found-asset (find-asset "test2.css")]
      (should (.contains (test2-debug-output) (:body found-asset)))))

  (it "compiles using the asset-url sass function"
    (let [found-asset (find-asset "test3.css")]
      (should (.contains (test3-debug-output) (:body found-asset)))))

  )
