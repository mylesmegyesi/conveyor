(ns conveyor.sass
  (:require [clojure.java.io     :refer [resource file]]
            [sass.core           :refer [render-string]]
            [conveyor.file-utils :refer [ensure-directory file-join]]
            [zweikopf.core       :refer [ruby-require]]
            [conveyor.config     :refer :all]))

(defn- resource-path [path]
  (.getPath (resource path)))

(defn- init-conveyor-sass []
  (ruby-require (resource-path "zweikopf-0.0.6/lib/zweikopf.rb"))
  (ruby-require (resource-path "jrclj-1.0.1/lib/jrclj.rb"))
  (ruby-require (resource-path "conveyor_sass/sass_functions.rb")))

(defn sass-cache-dir [{:keys [cache-dir]}]
  (file-join cache-dir "sass"))

(defn- compile-sass [config {:keys [absolute-path body] :as asset} input-extension output-extension]
  (assoc asset :body
         (render-string body
                        :load-paths (:load-paths config)
                        :syntax (keyword input-extension)
                        :style (if (:compress config) :compressed :expanded)
                        :filename absolute-path
                        :cache-location (sass-cache-dir config)
                        :trace-selectors true)))

(defn configure-sass [config]
  (init-conveyor-sass)
  (ensure-directory (file (sass-cache-dir config)))
  (add-compiler-config
    config
    (configure-compiler
      (add-input-extension "scss")
      (add-input-extension "sass")
      (add-output-extension "css")
      (set-compiler compile-sass))))

