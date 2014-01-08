(ns conveyor.strategy.factory
  (:require [conveyor.strategy.runtime :refer [make-runtime-pipeline]]
            [conveyor.strategy.precompiled :refer [make-precompiled-pipeline]]))

(defn make-pipeline [{:keys [strategy] :as config}]
  (case strategy
    :runtime (make-runtime-pipeline config)
    :precompiled (make-precompiled-pipeline config)
    (throw (IllegalArgumentException. (str "Invalid pipeline strategy " strategy)))))
