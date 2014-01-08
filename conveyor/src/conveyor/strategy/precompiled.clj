(ns conveyor.strategy.precompiled
  (:require [conveyor.file-utils :refer [file-input-stream get-extension replace-extension add-extension read-file file-join]]
            [conveyor.strategy.interface :refer [Pipeline]]
            [conveyor.strategy.util :refer :all]
            [conveyor.manifest :refer [read-manifest manifest-path]]))

(defn- throw-not-precompiled [config path]
  (throw
    (Exception.
      (format "%s is not in the manifest \"%s\". It has not been precompiled."
              path
              (manifest-path config)))))

(defn- read-file-in-output [{:keys [output-dir]} file-path]
  (file-input-stream (file-join output-dir file-path)))

(defn- -get-from-manifest [path config]
  (let [manifest (read-manifest config)]
    (get manifest path)))

(defn -find-asset [path config]
  (when-let [asset (-get-from-manifest path config)]
    (assoc asset :body (read-file-in-output config (:logical-path asset)))))

(def find-asset
  (-> -find-asset
      wrap-remove-digest
      wrap-suffix))

(def get-from-manifest
  (-> -get-from-manifest
      wrap-suffix))

(deftype PrecompiledPipeline [config]
  Pipeline
  (get-asset [this path]
    (find-asset path config))

  (get-logical-path [this path]
    (:logical-path (get-from-manifest path config)))

  (get-digest-path [this path]
    (:digest-path (get-from-manifest path config)))
)

(defn make-precompiled-pipeline [config]
  (PrecompiledPipeline. config))
