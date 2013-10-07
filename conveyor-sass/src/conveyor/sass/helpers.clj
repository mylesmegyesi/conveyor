(ns conveyor.sass.helpers
  (:require [conveyor.core :refer [asset-url] :rename {asset-url -asset-url}]))

(defn asset-url [path]
  (-asset-url path))

