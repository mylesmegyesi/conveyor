(ns conveyor.precompile
  (:require [conveyor.core :refer [find-assets]]
            [conveyor.file-utils :refer [file-join ensure-directory-of-file write-file with-file-cache]]
            [conveyor.manifest :refer [manifest-path]]
            [conveyor.pipeline :refer [pipeline pipeline-config]]))

(defn- build-manifest [assets]
  (reduce
    (fn [manifest asset]
      (assoc manifest
             (:logical-path asset)
             {:logical-path (:logical-path asset)
              :digest-path (:digest-path asset)
              :digest (:digest asset)
              :content-length (:content-length asset)}))
    {}
    assets))

(defn- write-asset-path [body path]
  (let [prefixed-path ((:path-prefixer (pipeline)) path)
        file-name (file-join (:output-dir (pipeline-config)) prefixed-path)]
    (ensure-directory-of-file file-name)
    (write-file file-name body)))

(defn- write-manifest [assets]
  (let [manifest (manifest-path (pipeline-config))]
    (ensure-directory-of-file manifest)
    (spit manifest (build-manifest assets)))
  assets)

(defn- write-assets [assets]
  (doseq [{:keys [body logical-path digest-path]} assets]
    (if digest-path
      (write-asset-path body digest-path)
      (write-asset-path body logical-path)))
  assets)

(defn precompile [paths]
  "Precompiles assets by finding assets using the runtime pipeline,
   writing asset-maps to the manifest, and writing files to the output-dir"
  (with-file-cache (:load-paths (pipeline-config))
    (-> (find-assets paths)
        (write-assets)
        (write-manifest))))
