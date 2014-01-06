(ns conveyor.compass-spec
  (:require [speclj.core :refer :all]
            [conveyor.sass :refer [configure-sass]]
            [conveyor.compass :refer :all]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]))

(describe "conveyor.compass"

  (defn normalize [path]
    (if (.startsWith path "jar:")
      (.substring path 4 (count path))
      path))

  (with stylesheets (directory-path "test_fixtures/stylesheets"))
  (with frameworks (normalize (resource-directory-path "compass-0.12.2/frameworks" "compass/templates/ellipsis/ellipsis.sass")))
  (with compass-templates (str @frameworks "/compass/templates"))
  (with blueprint-stylesheets (str @frameworks "/blueprint/stylesheets"))
  (with config (thread-pipeline-config
                 (add-directory-to-load-path "test_fixtures/stylesheets")
                 (assoc :plugins [:sass :compass])))

  (around [it]
    (with-pipeline-config @config
      (it)))

  (defn test-compass-output []
    (format
"/* on line 3 of %s/test_compass.scss */
.test-compass {
  -webkit-border-radius: 5px 4px;
  -moz-border-radius: 5px / 4px;
  border-radius: 5px / 4px;
}
"
      @stylesheets))

  (it "it can use the compass border radius helper"
    (let [found-asset (find-asset "test_compass.css")]
      (should= (test-compass-output) (:body found-asset))))

  (defn test-compass1-output []
    (format
"/* on line 8 of %s/ellipsis/ellipsis.sass
   from line 1 of %s/test_compass1.scss */
.ellipsis {
  white-space: nowrap;
  overflow: hidden;
  -ms-text-overflow: ellipsis;
  -o-text-overflow: ellipsis;
  text-overflow: ellipsis;
  -moz-binding: url('//xml/ellipsis.xml#ellipsis');
}

/* on line 3 of %s/test_compass1.scss */
.test-compass1 {
  white-space: nowrap;
  overflow: hidden;
  -ms-text-overflow: ellipsis;
  -o-text-overflow: ellipsis;
  text-overflow: ellipsis;
  -moz-binding: url('//xml/ellipsis.xml#ellipsis');
}
"
      @compass-templates
      @stylesheets
      @stylesheets))

  (it "it can use the compass ellipsis helper"
    (let [found-asset (find-asset "test_compass1.css")]
      (should= (test-compass1-output) (:body found-asset))))

  (defn test-compass2-output []
    (format
"/* on line 3 of %s/test_compass2.scss */
.test-compass2 {
  display: inline;
  float: 5px;
  display: block;
  margin: 0.7em 0.5em 0.7em 0;
  border-width: 1px;
  border-style: solid;
  font-family: \"Lucida Grande\", Tahoma, Arial, Verdana, sans-serif;
  font-size: 100%%;
  line-height: 130%%;
  font-weight: bold;
  text-decoration: none;
  cursor: pointer;
  padding: 5px 10px 5px 7px;
}
/* on line 75 of %s/blueprint/_buttons.scss, in `button-base'
   from line 86 of %s/blueprint/_buttons.scss, in `anchor-button'
   from line 4 of %s/test_compass2.scss */
.test-compass2 img {
  margin: 0 3px -3px 0 !important;
  padding: 0;
  border: none;
  width: 16px;
  height: 16px;
  float: none;
}
"
      @stylesheets
      @blueprint-stylesheets
      @blueprint-stylesheets
      @stylesheets))

  (it "can use the blueprint button helper"
    (let [found-asset (find-asset "test_compass2.css")]
      (should= (test-compass2-output) (:body found-asset))))

  (defn test-compass3-output []
    (format
"/* on line 3 of %s/test_compass3.scss */
.test-compass3 {
  margin: 10px;
}
"
      @stylesheets))

  (it "can the blueprint templates"
    (let [found-asset (find-asset "test_compass3.css")]
      (should= (test-compass3-output) (:body found-asset))))

  )
