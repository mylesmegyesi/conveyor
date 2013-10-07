(ns conveyor.finder.factory
  (:require [conveyor.finder.load-path :refer [make-load-path-asset-finder]]
            [conveyor.finder.precompiled :refer [make-precompiled-asset-finder]]))

(defn make-asset-finder [{:keys [asset-finder] :as config}]
  (case asset-finder
    :load-path (make-load-path-asset-finder config)
    :precompiled (make-precompiled-asset-finder config)
    (throw (IllegalArgumentException. (str "Invalid asset-finder " asset-finder)))))
