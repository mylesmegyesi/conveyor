(ns conveyor.core
  (:require [clojure.string :as clj-str]
            [conveyor.file-utils :refer [get-extension remove-extension add-extension file-join]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.compress :refer [compress-asset]]
            [conveyor.config :refer [compile? compress?]]
            [conveyor.finder.interface :refer [get-asset get-logical-path get-digest-path]]
            [conveyor.finder.factory :refer [make-asset-finder]]))

(declare ^:dynamic *pipeline*)
(declare ^:dynamic *pipeline-config*)

(defn pipeline []
  (if (bound? #'*pipeline*)
    *pipeline*
    (throw (Exception. "Pipeline config not bound."))))

(defn pipeline-config []
  (if (bound? #'*pipeline-config*)
    *pipeline-config*
    (throw (Exception. "Pipeline config not bound."))))

(defn identity-pipeline-fn [config path extension asset]
  asset)

(defn- build-pipeline-fns [config]
  (if (:pipeline-enabled config)
    [(if (compile? config)
       compile-asset
       identity-pipeline-fn)
     (if (compress? config)
       compress-asset
       identity-pipeline-fn)]
    []))

(defn- build-pipeline-fn [config]
  (let [pipeline-fns (build-pipeline-fns config)]
    (fn [path extension asset]
      (reduce
        (fn [asset f]
          (f config path extension asset))
        asset
        pipeline-fns))))

(defn- build-path-prefixer-fn [config]
  (if-let [prefix (:prefix config)]
    (fn [path] (file-join "/" prefix path))
    (fn [path] (file-join "/" path))))

(defn build-path-finder-fn [finder config]
  (if (:use-digest-path config)
    (fn [path extension] (get-digest-path finder path extension))
    (fn [path extension] (get-logical-path finder path extension))))

(defn build-url-builder-fn [config]
  (if-let [asset-host (:asset-host config)]
    (fn [path] (str asset-host path))
    (fn [path] path)))

(defn build-pipeline [config]
  (let [finder (make-asset-finder config)]
    {:finder finder
     :pipeline-fn (build-pipeline-fn config)
     :url-builder (build-url-builder-fn config)
     :path-prefixer (build-path-prefixer-fn config)
     :path-finder (build-path-finder-fn finder config)}))

(defn- do-get [path extension]
  (let [{:keys [finder pipeline-fn]} (pipeline)]
    (when-let [asset (get-asset finder path extension)]
      (pipeline-fn path extension asset))))

(defn- normalize-paths [asset]
  (if-let [extension (:extension asset)]
    (-> asset
      (assoc :logical-path (add-extension (:logical-path asset) extension))
      (assoc :digest-path (add-extension (:digest-path asset) extension)))
    asset))

(defmacro with-pipeline-config [config & body]
  `(let [config# ~config]
     (binding [*pipeline-config* config#
               *pipeline* (build-pipeline config#)]
       ~@body)))

(defn path-and-extention-variations [path]
  (let [output-extension (get-extension path)]
    [[path output-extension]
     [path ""]
     [(remove-extension path) output-extension]]))

(defn- call-fn-for-path [f asset-path]
  (loop [variations (path-and-extention-variations asset-path)]
    (when (not (zero? (count variations)))
      (let [[path extension] (first variations)]
        (if-let [found (f path extension)]
          found
          (recur (rest variations)))))))

(defn- remove-asset-digest [path extension]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      (let [without-match (clj-str/replace path match "")]
        (if (empty? extension)
          [digest without-match]
          [digest (str without-match "." extension)]))
      [nil path])))

(defn find-asset
  ([path]
    (call-fn-for-path find-asset path))
  ([path extension]
    (let [[digest path] (remove-asset-digest path extension)]
      (when-let [asset (do-get path extension)]
        (if digest
          (when (= digest (:digest asset))
            (normalize-paths asset))
          (normalize-paths asset))))))

(defmacro throw-unless-found [path & body]
  `(if-let [asset# ~@body]
     asset#
     (throw (Exception. (format "Asset not found: %s" ~path)))))

(defn find-asset!
  ([path]
    (throw-unless-found path (find-asset path)))
  ([path extension]
    (throw-unless-found path (find-asset path extension))))

(defn- get-path [path extension]
  (let [pipe (pipeline)
        prefix-fn (:path-prefixer pipe)
        path-finder-fn (:path-finder pipe)]
    (if-let [path (path-finder-fn path extension)]
      (prefix-fn path))))

(defn- asset-path
  ([path]
    (throw-unless-found path (call-fn-for-path get-path path)))
  ([path extension]
    (throw-unless-found path (get-path path extension))))

(defn- build-url [path]
  ((:url-builder (pipeline)) path))

(defn asset-url
  ([path]
    (build-url (asset-path path)))
  ([path extension]
    (build-url (asset-path path extension))))
