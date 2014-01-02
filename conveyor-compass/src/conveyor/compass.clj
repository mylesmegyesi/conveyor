(ns conveyor.compass
  (:require [clojure.java.io :refer [resource]]
            [zweikopf.core :refer [ruby-require]]
            [sass.core]
            [conveyor.core :refer [add-resource-directory-to-load-path]]))

(defn- resource-path [path]
  (.getPath (resource path)))

(defn- init-conveyor-compass []
  (ruby-require (resource-path "chunky_png-1.2.7/lib/chunky_png.rb"))
  (ruby-require (resource-path "compass-0.12.2/lib/compass.rb"))
  (ruby-require (resource-path "compass-0.12.2/lib/compass/sass_extensions.rb"))
  (ruby-require (resource-path "fssm-0.2.10/lib/fssm.rb")))

(defn configure-compass [config]
  (init-conveyor-compass)
  (-> config
    (add-resource-directory-to-load-path "compass-0.12.2/frameworks/compass/stylesheets" "_compass.scss")
    (add-resource-directory-to-load-path "compass-0.12.2/frameworks/compass/templates" "ellipsis/ellipsis.sass")
    (add-resource-directory-to-load-path "compass-0.12.2/frameworks/blueprint/stylesheets" "_blueprint.scss")
    (add-resource-directory-to-load-path "compass-0.12.2/frameworks/blueprint/templates" "project/screen.sass")))

