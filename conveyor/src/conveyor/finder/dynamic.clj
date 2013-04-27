(ns conveyor.finder.dynamic
  (:require [clojure.java.io :refer [file]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [conveyor.config :refer [compile?]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.file-utils :refer :all]
            [conveyor.finder.interface :refer [AssetFinder]]))

(defn- build-asset [logical-file-path output-extension requested-extension asset-body]
  (let [digest (md5 asset-body)
        extension (or output-extension requested-extension)
        file-name (remove-extension logical-file-path)]
    {:body asset-body
     :logical-path (add-extension file-name extension)
     :digest digest
     :digest-path (add-extension (str file-name "-" digest) extension)}))

(defn- format-asset-path [asset-path]
  (format "\"%s\"" asset-path))

(defn- throw-multiple-found-exception [requested-path found-paths]
  (throw
    (Exception.
      (format "Search for %s returned multiple results: %s"
              (format-asset-path requested-path)
              (join ", " (map format-asset-path found-paths))))))

(defn- files-with-compiler-extensions
  ([paths file-path extensions requested-extension]
    (files-with-compiler-extensions paths file-path extensions requested-extension (fn [path extension] (add-extension path extension))))
  ([paths file-path extensions requested-extension build-logical-path]
    (let [without-ext (remove-extension file-path)]
      (reduce
        (fn [paths extension]
          (-> paths
            (conj {:relative-path (add-extension file-path extension)
                   :logical-path (build-logical-path file-path extension)})
            (as-> paths
              (if (empty? requested-extension)
                paths
                (conj paths {:relative-path (add-extension without-ext extension)
                             :logical-path (build-logical-path without-ext extension)})))))
        paths
        extensions))))

(defn- index-file [paths file-path extensions requested-extension]
  (files-with-compiler-extensions paths (str file-path "/index") extensions requested-extension (fn [path extension] (add-extension file-path extension))))

(defn requested-file [paths file-path requested-extension]
  (if (empty? (get-extension file-path))
    (if (empty? requested-extension)
      paths
      (let [relative-path (add-extension file-path requested-extension)]
        (conj paths {:relative-path relative-path
                     :logical-path relative-path})))
    (conj paths {:relative-path file-path
                 :logical-path file-path})))

(defn- extensions-from-compiler [compiler]
  (concat
    (:input-extensions compiler)
    (:output-extensions compiler)))

(defn- extensions-from-compilers [compilers]
  (distinct (mapcat extensions-from-compiler compilers)))

(defn- compiler-extensions [compilers requested-extension]
  (extensions-from-compilers
    (if (empty? requested-extension)
      compilers
      (filter
        (fn [compiler]
          (some
            #(= % requested-extension)
            (extensions-from-compiler compiler)))
        compilers))))

(defn- compilers [config]
  (if (compile? config)
    (:compilers config)
    []))

(defn- build-possible-files [config path extension]
  (let [extensions (compiler-extensions (compilers config) extension)]
    (distinct
      (-> []
        (requested-file path extension)
        (files-with-compiler-extensions path extensions extension)
        (index-file path extensions extension)))))

(defn- build-possible-input-files [load-paths]
  (reduce
    (fn [files load-path]
      (reduce
        (fn [files file]
          (conj files
                {:absolute-path file
                 :relative-path (replace-first file (str load-path "/") "")}))
        files
        (list-files load-path)))
    []
    load-paths))

(defn- match-files [input-files potential-files]
  (distinct
    (reduce
      (fn [matches {:keys [logical-path] :as potential-file}]
        (reduce
          (fn [matches {:keys [relative-path] :as output-file}]
            (if (= relative-path (:relative-path potential-file))
              (conj matches (assoc output-file :logical-path logical-path))
              matches))
          matches
          input-files))
      []
      potential-files)))

(defn- find-file [config path extension]
  (let [input-files (build-possible-input-files (:load-paths config))
        files-to-read (build-possible-files config path extension)
        matching-files (match-files input-files files-to-read)
        num-found-assets (count matching-files)]
    (cond
      (> num-found-assets 1)
      (throw-multiple-found-exception path (map :absolute-path matching-files))
      (= num-found-assets 1)
      (let [{:keys [logical-path absolute-path]} (first matching-files)]
        {:absolute-path absolute-path
         :extension (get-extension logical-path)
         :logical-path (remove-extension logical-path)}))))

(defn find-asset [config path extension]
  (if-let [file (find-file config path extension)]
    (let [{:keys [logical-path absolute-path]} file
          body (read-file absolute-path)
          digest (md5 body)]
      (-> file
        (assoc :body body)
        (assoc :digest digest)
        (assoc :digest-path (str logical-path "-" digest))))))

(deftype DynamicAssetFinder [config]
  AssetFinder

  (get-asset [this path extension]
    (find-asset config path extension))

  (get-logical-path [this path extension]
    (when-let [file (find-file config path extension)]
      (add-extension (:logical-path file) extension)))

  (get-digest-path [this path extension]
    (add-extension (:digest-path (find-asset config path extension)) extension))

  )

(defn make-dynamic-asset-finder [config]
  (DynamicAssetFinder. config))

