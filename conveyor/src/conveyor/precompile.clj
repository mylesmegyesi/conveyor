(ns conveyor.precompile
  (:require [conveyor.core :refer [find-asset! pipeline pipeline-config]]
            [conveyor.file-utils :refer [file-join ensure-directory-of-file write-file]]
            [conveyor.finder.interface :refer :all]
            [conveyor.manifest :refer [manifest-path]]
            ))

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

(defn add-found-paths [paths regex finder]
  (let [matches (get-paths-from-regex finder regex)]
    (if (empty? matches)
      paths
      (apply conj paths matches))))

(defn filter-regex [paths]
  (let [finder (:finder (pipeline))]
    (reduce
      (fn [paths path]
        (if (string? path)
          (conj paths path)
          (add-found-paths paths path finder)))
      #{}
      paths)))

(defn precompile [paths]
  (let [filtered-paths (filter-regex paths)]
    (-> (doall (map #(find-asset! %) filtered-paths))
      (write-assets)
      (write-manifest))))
