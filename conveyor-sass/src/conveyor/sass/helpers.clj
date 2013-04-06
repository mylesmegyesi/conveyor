(ns conveyor.sass.helpers
  (:require [conveyor.core :refer [asset-path asset-url] :rename {asset-path -asset-path asset-url -asset-url}]))

(declare ^:dynamic *current-config*)

(defn asset-path [path]
  (first (-asset-path *current-config* path)))

(defn asset-url [path]
  (first (-asset-url *current-config* path)))

