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

(defn- get-from-manifest [config path extension]
  (let [manifest (read-manifest config)
        possible-paths [(replace-extension path extension)
                        (add-extension path extension)]]
    (some #(get manifest %) possible-paths)))

(defn find-asset [config path extension]
  (when-let [asset (get-from-manifest config path extension)]
    (assoc asset :body (read-file-in-output config (:logical-path asset)))))

(deftype StaticAssetFinder [config]
  AssetFinder
  (get-asset [this path extension]
    (find-asset config path extension))

  (get-logical-path [this path extension]
    (:logical-path (get-from-manifest config path extension)))

  (get-digest-path [this path extension]
    (:digest-path (get-from-manifest config path extension)))

  )

(defn make-static-asset-finder [config]
  (StaticAssetFinder. config))
