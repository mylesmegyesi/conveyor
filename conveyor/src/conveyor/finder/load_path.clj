(ns conveyor.finder.load-path
  (:require [clojure.java.io :refer [file]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.file-utils :refer :all]
            [conveyor.finder.interface :refer [AssetFinder]]))

(defn- format-asset-path [asset-path]
  (format "\"%s\"" asset-path))

(defn- throw-multiple-found-exception [requested-path found-paths]
  (throw
    (Exception.
      (format "Search for %s returned multiple results: %s"
              (format-asset-path requested-path)
              (join ", " (map format-asset-path found-paths))))))

(defn- files-with-compiler-extensions
  ([paths file-path extensions]
    (files-with-compiler-extensions paths file-path extensions (fn [path extension] (add-extension path extension))))
  ([paths file-path extensions build-logical-path]
    (let [without-ext (remove-extension file-path)]
      (reduce
        (fn [paths extension]
          (-> paths
            (conj {:relative-path (add-extension file-path extension)
                   :logical-path (build-logical-path file-path extension)})
            (as-> paths
              (conj paths {:relative-path (add-extension without-ext extension)
                           :logical-path (build-logical-path without-ext extension)}))))
        paths
        extensions))))

(defn- index-file [paths file-path extensions]
  (files-with-compiler-extensions paths (str (remove-extension file-path) "/index") extensions (fn [path extension] (replace-extension file-path extension))))

(defn requested-file [paths file-path]
  (if (empty? (get-extension file-path))
    (let [relative-path file-path];(add-extension file-path requested-extension)]
      (conj paths {:relative-path relative-path
                   :logical-path relative-path}))
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
  (:compilers config))

(defn- build-possible-files [config path requested-extension]
  (let [extensions (compiler-extensions (compilers config) requested-extension)]
    (distinct
      (-> []
        (requested-file path)
        (files-with-compiler-extensions path extensions)
        (index-file path extensions)))))

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

(defn- find-file [config path]
  (let [requested-extension (get-extension path)
        input-files (build-possible-input-files (:load-paths config))
        files-to-read (build-possible-files config path requested-extension)
        matching-files (match-files input-files files-to-read)
        num-found-assets (count matching-files)]
    (cond
      (> num-found-assets 1)
      (throw-multiple-found-exception path (map :absolute-path matching-files))
      (= num-found-assets 1)
      (let [{:keys [logical-path absolute-path]} (first matching-files)]
        {:absolute-path absolute-path
         :extension (get-extension logical-path)
         :logical-path (replace-extension logical-path requested-extension)}))))

(defn find-asset [config path]
  (if-let [file (find-file config path)]
    (let [{:keys [logical-path absolute-path]} file
          body (read-file absolute-path)
          digest (md5 body)]
      (-> file
        (assoc :body body)
        (assoc :digest digest)
        (assoc :digest-path (add-extension
                              (str (remove-extension logical-path) "-" digest)
                              (get-extension logical-path)))))))

(deftype LoadPathAssetFinder [config]
  AssetFinder

  (get-asset [this path]
    (find-asset config path))

  (get-logical-path [this path]
    (when-let [file (find-file config path)]
      (:logical-path file)))

  (get-digest-path [this path]
    (:digest-path (find-asset config path)))

  )

(defn make-load-path-asset-finder [config]
  (LoadPathAssetFinder. config))

