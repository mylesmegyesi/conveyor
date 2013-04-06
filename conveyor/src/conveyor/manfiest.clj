(ns conveyor.manfiest
  (:require [clojure.edn :refer [read-string] :rename {read-string read-edn}]
            [conveyor.file-utils :refer [file-join]]))

(defn manifest-path [{:keys [manifest output-dir prefix]}]
  (if manifest
    manifest
    (file-join output-dir prefix "manifest.edn")))

(def read-manifest
  (memoize
    (fn [config]
      (read-edn (slurp (manifest-path config))))))

