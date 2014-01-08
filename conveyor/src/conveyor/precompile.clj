(ns conveyor.precompile
  (:require [conveyor.core :refer [find-asset! pipeline pipeline-config]]
            [conveyor.file-utils :refer [file-join ensure-directory-of-file write-file]]
            [conveyor.manifest :refer [manifest-path]]))

(defn- build-manifest [assets]
  (reduce
    (fn [manifest asset]
      (assoc manifest
             (:logical-path asset)
             {:logical-path (:logical-path asset)
              :digest-path (:digest-path asset)
              :digest (:digest asset)}))
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
    (write-asset-path body logical-path)
    (if digest-path (write-asset-path body digest-path)))
  assets)

(defn precompile [paths]
  (-> (flatten (doall (map #(find-asset! %) paths)))
    (write-assets)
    (write-manifest)))
