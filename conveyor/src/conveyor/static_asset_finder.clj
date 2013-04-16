(ns conveyor.static-asset-finder
  (:require [conveyor.file-utils :refer [get-extension replace-extension add-extension]]
            [conveyor.manfiest :refer [read-manifest manifest-path]]))

(defn- throw-not-precompiled [config path]
  (throw
    (Exception.
      (format "%s is not in the manifest \"%s\". It has not been precompiled."
              path
              (manifest-path config)))))

(defn find-asset
  ([config path] (find-asset config path (get-extension path)))
  ([config path extension]
    (let [manifest (read-manifest config)
          possible-paths [(replace-extension path extension)
                          (add-extension path extension)]
          file-path (some #(get manifest %) possible-paths)]
      (if file-path
        {:logical-path file-path}
        (throw-not-precompiled config path)))))

