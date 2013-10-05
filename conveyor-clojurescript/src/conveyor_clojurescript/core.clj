(ns conveyor-clojurescript.core
  (:require [conveyor.config :refer :all]
            [cljs.closure :refer [build]]))

(defn- compiler-for-options [options]
  (fn [config asset input-extension output-extension]
    (assoc asset :body (build (:absolute-path asset) options))))

(defn configure-coffeescript
  ([config] (configure-coffeescript config {:optimizations :advanced}))
  ([config options]
   (add-compiler-config
     config
     (configure-compiler
       (add-input-extension "cljs")
       (add-output-extension "js")
       (set-compiler (compiler-for-options options))))))
