(ns conveyor.finder.dynamic
  (:require [clojure.java.io :refer [file]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.context :refer :all]
            [conveyor.file-utils :refer :all]
            [conveyor.finder.interface :refer [AssetFinder]]))

(defn- build-asset [logical-file-path output-extension asset-body]
  (let [digest (md5 asset-body)
        file-name (remove-extension logical-file-path)]
    {:body asset-body
     :logical-path (add-extension file-name output-extension)
     :digest digest
     :digest-path (add-extension (str file-name "-" digest) output-extension)}))

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

(defn- build-possible-files [context]
  (let [asset-path (get-requested-path context)
        requested-extension (get-requested-extension context)
        extensions (compiler-extensions (:compilers (get-config context)) requested-extension)]
    (distinct
      (-> []
        (requested-file asset-path requested-extension)
        (files-with-compiler-extensions asset-path extensions requested-extension)
        (index-file asset-path extensions requested-extension)))))

(defn- build-possible-input-files [context]
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
    (:load-paths (get-config context))))

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

(defn- find-file [context]
  (let [input-files (build-possible-input-files context)
        files-to-read (build-possible-files context)
        matching-files (match-files input-files files-to-read)
        num-found-assets (count matching-files)]
    (cond
      (> num-found-assets 1)
      (throw-multiple-found-exception
        (get-requested-path context)
        (map :absolute-path matching-files))
      (= num-found-assets 1)
      (first matching-files))))

(defn- read-asset [context on-asset-read]
  (if-let [file (find-file context)]
    (let [{:keys [absolute-path logical-path]} file
          body (read-file absolute-path)]
      (on-asset-read
        (-> context
          (set-asset-body body)
          (set-found-path absolute-path)
          (set-base-path logical-path)
          (set-found-extension (get-extension absolute-path)))))))

(defn- serve-asset [context]
  (read-asset
    context
    (fn [context]
      (-> context
        compile-asset
        (as-> result-context
              (build-asset
                (get-base-path result-context)
                (get-asset-extension result-context)
                (get-asset-body result-context)))))))

(defn- make-context [config path extension]
  (make-serve-context
    (set-config config)
    (set-requested-path path)
    (set-requested-extension extension)))

(defn find-asset [config path extension]
  (serve-asset (make-context config path extension)))

(deftype DynamicAssetFinder [config]
  AssetFinder
  (get-asset [this path extension]
    (serve-asset (make-context config path extension)))

  (get-logical-path [this path extension]
    (when-let [file (find-file (make-context config path extension))]
      (replace-extension (:logical-path file) extension)))

  (get-digest-path [this path extension]
    (:digest-path (find-asset config path extension)))

  )

(defn make-dynamic-asset-finder [config]
  (DynamicAssetFinder. config))

