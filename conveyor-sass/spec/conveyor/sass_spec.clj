(ns conveyor.sass-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer [find-asset asset-path asset-url]]
            [conveyor.sass :refer :all]))

(describe "conveyor.sass"

  (with stylesheets1 (directory-path "test_fixtures/stylesheets1"))
  (with config (thread-pipeline-config
                 (configure-sass)
                 (add-directory-to-load-path "test_fixtures/stylesheets1")))

  (with test4-path (asset-path @config "test4.png"))
  (with test4-url (asset-url @config "test4.png"))

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
      @test4-path
      @stylesheets1
      @test4-url))

  (it "compiles a scss file"
    (let [found-asset (find-asset @config "test1.css")]
      (should (.contains (test1-debug-output) (:body found-asset)))))

  (it "compiles a sass file"
    (let [found-asset (find-asset @config "test2.css")]
      (should (.contains (test2-debug-output) (:body found-asset)))))

  (it "compiles using the asset-path and asset-url sass function"
    (let [found-asset (find-asset @config "test3.css")]
      (should (.contains (test3-debug-output) (:body found-asset)))))

  )
