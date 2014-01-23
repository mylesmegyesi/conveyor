(ns example.precompile
  (:require [conveyor.core :refer [with-pipeline-config]]
            [conveyor.precompile :refer [precompile]]
            [example.core :refer [conveyor-config]]))

(def assets ["application.css" #".*\.js"])

(defn -main [& args]
  (with-pipeline-config conveyor-config
    (precompile assets)))
