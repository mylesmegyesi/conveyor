(ns conveyor.finder.factory
  (:require [conveyor.finder.dynamic :refer [make-dynamic-asset-finder]]
            [conveyor.finder.static :refer [make-static-asset-finder]]))

(defn make-asset-finder [type config]
  (case type
    :dynamic (make-dynamic-asset-finder config)
    :static (make-static-asset-finder config)))

