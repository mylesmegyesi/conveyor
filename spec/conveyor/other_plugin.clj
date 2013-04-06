(ns conveyor.other-plugin
  (:require [conveyor.config :refer [add-directory-to-load-path]]))

(defn configure-other-plugin [config]
  (add-directory-to-load-path config "spec/fixtures/public/javascripts"))

