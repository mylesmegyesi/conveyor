(ns conveyor.strategy.runtime
  (:require [clojure.java.io :refer [as-file]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [conveyor.file-utils :refer :all]
            [conveyor.strategy.util :refer :all]
            [conveyor.strategy.interface :refer [Pipeline]]))

(defn- compressor-for-extension [config extension]
  (first
    (filter
      #(= extension (:input-extension %))
      (:compressors config))))

(defn compress-asset [config path asset]
  (let [asset-extension (:extension asset)]
    (if-let [compressor (compressor-for-extension config asset-extension)]
      (assoc asset :body ((:compressor compressor)
                            config (:body asset) (:absolute-path asset)))
      asset)))

(defn- throw-multiple-output-exensions-with-no-requested-output-extension [requested-path found-path output-extensions]
  (throw (Exception. (format "Search for \"%s\" found \"%s\". However, you did not request an output extension and the matched compiler has multiple output extensions: %s"
                             requested-path
                             found-path
                             (join ", " output-extensions)))))

(defn- throw-multiple-compilers [input-extension output-extension]
  (throw (Exception. (format "Found multiple compilers to handle input extension \"%s\" and output extension \"%s\""
                             input-extension output-extension))))

(defn- compilers-for-extension [compilers input-extension output-extension]
  (filter
    (fn [compiler]
      (and (some #(= % input-extension) (:input-extensions compiler))
           (if (empty? output-extension)
             true
             (some #(= % output-extension) (:output-extensions compiler)))))
    compilers))

(defn- do-compile [config asset compiler-fn found-extension output-extension]
  (-> (compiler-fn config asset found-extension output-extension)
    (assoc :extension output-extension)))

(defn compile-asset [config path asset]
  (let [extension (get-extension path)
        found-extension (:extension asset)
        compilers (compilers-for-extension (:compilers config) found-extension extension)
        num-compilers (count compilers)
        compiler (first compilers)
        output-extensions (:output-extensions compiler)]
    (cond
      (> num-compilers 1)
      (throw-multiple-compilers found-extension extension)
      (= num-compilers 1)
      (if (empty? extension)
        (if (= 1 (count output-extensions))
          (do-compile config asset (:compiler compiler) found-extension (first output-extensions))
          (throw-multiple-output-exensions-with-no-requested-output-extension
            path (:absolute-path asset) output-extensions))
        (do-compile config asset (:compiler compiler) found-extension extension))
      :else
      asset)))

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
          (conj paths {:relative-path (add-extension without-ext extension)
                       :logical-path (build-logical-path without-ext extension)}))
        paths
        extensions))))

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
        (files-with-compiler-extensions path extensions)))))

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

(defn all-possible-output [{:keys [load-paths] :as config}]
  (let [possible-input (build-possible-input-files load-paths)
        possible-paths (map :relative-path possible-input)
        possible-output (map #(build-possible-files config % (get-extension %)) possible-paths)]
    (flatten possible-output)))

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

(defn add-digest [{:keys [body logical-path] :as asset}]
  (let [digest (md5 body)]
    (-> asset
      (assoc :digest digest)
      (assoc :digest-path (add-extension
                            (str (remove-extension logical-path) "-" digest)
                            (get-extension logical-path))))))

(defn compile? [{:keys [extension logical-path]} {:keys [compilers] :as config}]
  (and
    (:pipeline-enabled config)
    (:compile config)
    (not (empty? (compilers-for-extension compilers extension (get-extension logical-path))))))

(defn compress? [{:keys [extension]} config]
  (and
    (:pipeline-enabled config)
    (:compress config)
    (compressor-for-extension config extension)))

(defn apply-compile [handlers file config]
  (if (compile? file config)
    (conj handlers
      (fn [asset]
        (compile-asset config (:logical-path file) asset)))
    handlers))

(defn apply-compress [handlers file config]
  (if (compress? file config)
    (conj handlers
      (fn [asset]
        (compress-asset config (:logical-path file) asset)))
    handlers))

(defn apply-digest-path [handlers config]
  (if (:use-digest-path config)
    (conj handlers add-digest)
    handlers))

(defn build-pipeline [file config]
  (-> []
    (apply-compile file config)
    (apply-compress file config)))

(def get-file
  (-> find-file
      wrap-remove-digest
      wrap-suffix))

(defn find-asset [path config]
  (when-let [{:keys [absolute-path] :as file} (get-file path config)]
    (let [pipeline (build-pipeline file config)
          body (if (seq pipeline) (read-file absolute-path) (as-file absolute-path))
          asset (assoc file :body body)
          pipeline (apply-digest-path pipeline config)]
      (if (seq pipeline)
        ((apply comp pipeline) asset)
        asset))))

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

