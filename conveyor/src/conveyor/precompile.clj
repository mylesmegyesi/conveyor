(ns conveyor.precompile
  (:require [conveyor.core :refer [find-asset! pipeline pipeline-config]]
            [conveyor.file-utils :refer [file-join ensure-directory-of-file write-file with-file-cache]]
            [conveyor.strategy.runtime :refer [all-possible-output]]
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
    (if digest-path
      (write-asset-path body digest-path)
      (write-asset-path body logical-path)))
  assets)

(defn regex? [path]
  (= (re-pattern path) path))

(defn find-matches [path possible-files]
  (if (regex? path)
    (reduce
      (fn [files {:keys [relative-path]}]
        (if (re-matches path relative-path)
          (conj files relative-path)
          files))
      []
      possible-files)
    [path]))

(defn find-regex-matches [paths possible-files]
  (-> (map #(find-matches % possible-files) paths)
      (flatten)
      (set)))

(defn filter-regex [paths]
  (if (some regex? paths)
    (find-regex-matches paths (all-possible-output (pipeline-config)))
    paths))

(defn precompile [paths]
  (with-file-cache (:load-paths (pipeline-config))
    (let [filtered-paths (filter-regex paths)]
      (-> (flatten (doall (map #(find-asset! %) filtered-paths)))
        (write-assets)
        (write-manifest)))))
