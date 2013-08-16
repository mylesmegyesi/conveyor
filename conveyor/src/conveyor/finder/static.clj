(ns conveyor.finder.static
  (:require [conveyor.file-utils :refer [get-extension replace-extension add-extension read-file file-join]]
            [conveyor.finder.interface :refer [AssetFinder]]
            [conveyor.manifest :refer [read-manifest manifest-path]]))

(defn- throw-not-precompiled [config path]
  (throw
    (Exception.
      (format "%s is not in the manifest \"%s\". It has not been precompiled."
              path
              (manifest-path config)))))

(defn- read-file-in-output [{:keys [output-dir]} file-path]
  (read-file (file-join output-dir file-path)))

(defn- get-from-manifest [config path]
  (let [manifest (read-manifest config)]
    (get manifest path)))

(defn find-asset [config path]
  (when-let [asset (get-from-manifest config path)]
    (assoc asset :body (read-file-in-output config (:logical-path asset)))))

(deftype StaticAssetFinder [config]
  AssetFinder
  (get-asset [this path]
    (find-asset config path))

  (get-logical-path [this path]
    (:logical-path (get-from-manifest config path)))

  (get-digest-path [this path]
    (:digest-path (get-from-manifest config path)))

  )

(defn make-static-asset-finder [config]
  (StaticAssetFinder. config))
