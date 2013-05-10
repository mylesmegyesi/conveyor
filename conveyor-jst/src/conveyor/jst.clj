(ns conveyor.jst
  (:require [conveyor.config :refer [add-compiler-config configure-compiler add-input-extension add-output-extension set-compiler]]
            [cheshire.core :refer [generate-string]]))

(defn- compile-jst [config asset input-extension output-extension]
  (assoc asset :body
         (format "(function() {this.JST || (this.JST = {}); this.JST[%s] = %s; }).call(this);"
                 (generate-string (:logical-path asset))
                 (generate-string (:body asset)))))

(defn configure-jst [config]
  (add-compiler-config
    config
    (configure-compiler
      (add-input-extension "jst")
      (add-output-extension "js")
      (set-compiler compile-jst))))

