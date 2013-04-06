(ns conveyor.test-plugin
  (:require [conveyor.config :refer [add-directory-to-load-path]]))

(defn configure-test-plugin [config]
  (->
    (assoc config :test-config "configured")
    (add-directory-to-load-path "test_fixtures/public/stylesheets")))
