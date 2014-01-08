(ns conveyor.strategy.runtime
  (:require [clojure.java.io :refer [as-file]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.compress :refer [compress-asset]]
            [conveyor.file-utils :refer :all]
            [conveyor.strategy.util :refer :all]
            [conveyor.strategy.interface :refer [Pipeline]]))

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

(defn- find-file [path config]
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

(defn return-static-file? [file requested-path {:keys [compress use-digest-path]}]
  (and
    (not (or compress use-digest-path))
    (= (get-extension (:logical-path file)) (get-extension (:absolute-path file)))))

(defn -find-asset [path config]
  (if-let [file (find-file path config)]
    (let [{:keys [logical-path absolute-path]} file]
      (if (return-static-file? file path config)
        (assoc file :body (as-file absolute-path))
        (assoc file :body (read-file absolute-path))))))

(defn compile? [asset config]
  (and (string? (:body asset)) (:pipeline-enabled config) (:compile config)))

(defn compress? [config]
  (and (:pipeline-enabled config) (:compress config)))

(defn wrap-compile [handler]
  (fn [path config]
    (let [asset (handler path config)]
      (if (compile? asset config)
        (compile-asset config path asset)
        asset))))

(defn wrap-compress [handler]
  (fn [path config]
    (let [asset (handler path config)]
      (if (compress? config)
        (compress-asset config path asset)
        asset))))

(defn add-digest [{:keys [body logical-path] :as asset}]
  (let [digest (md5 body)]
    (-> asset
      (assoc :digest digest)
      (assoc :digest-path (add-extension
                            (str (remove-extension logical-path) "-" digest)
                            (get-extension logical-path))))))

(defn wrap-add-digest [handler]
  (fn [path config]
    (let [asset (handler path config)]
      (if (and asset (:use-digest-path config))
        (add-digest asset)
        asset))))

(defn all-possible-output [{:keys [load-paths] :as config}]
  (let [possible-input (build-possible-input-files load-paths)
        possible-paths (map :relative-path possible-input)
        possible-output (map #(build-possible-files config % (get-extension %)) possible-paths)]
    (flatten possible-output)))

(defn find-regex-matches [regex config]
  (let [possible-files (all-possible-output config)]
    (reduce
      (fn [files {:keys [relative-path]}]
        (if (re-matches regex relative-path)
          (conj files relative-path)
          files))
      #{}
      possible-files)))

(defn regex? [path]
  (= (re-pattern path) path))

(defn wrap-regex [handler]
  (fn [path config]
    (if (regex? path)
      (let [matches (find-regex-matches path config)]
        (when (not (empty? matches))
          (map #(handler % config) matches)))
      (handler path config))))

(def find-asset
  (-> -find-asset
      wrap-compile
      wrap-compress
      wrap-add-digest
      wrap-remove-digest
      wrap-suffix
      wrap-regex))

(def get-file
  (-> find-file
      wrap-suffix))

(deftype RuntimePipeline [config]
  Pipeline

  (get-asset [this path]
    (find-asset path config))

  (get-logical-path [this path]
    (when-let [file (get-file path config)]
      (:logical-path file)))

  (get-digest-path [this path]
    (:digest-path (find-asset path config)))
)

(defn make-runtime-pipeline [config]
  (RuntimePipeline. config))

