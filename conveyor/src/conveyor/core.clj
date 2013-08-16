(ns conveyor.core
  (:require [clojure.string :as clj-str]
            [conveyor.file-utils :refer [file-join]]
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

(defn identity-pipeline-fn [config path asset]
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
    (fn [path asset]
      (reduce
        (fn [asset f]
          (f config path asset))
        asset
        pipeline-fns))))

(defn- build-path-prefixer-fn [config]
  (if-let [prefix (:prefix config)]
    (fn [path] (file-join "/" prefix path))
    (fn [path] (file-join "/" path))))

(defn build-path-finder-fn [finder config]
  (if (:use-digest-path config)
    (fn [path] (get-digest-path finder path))
    (fn [path] (get-logical-path finder path))))

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

(defn- do-get [path]
  (let [{:keys [finder pipeline-fn]} (pipeline)]
    (when-let [asset (get-asset finder path)]
      (pipeline-fn path asset))))

(defmacro with-pipeline-config [config & body]
  `(let [config# ~config]
     (binding [*pipeline-config* config#
               *pipeline* (build-pipeline config#)]
       ~@body)))

(defn- remove-asset-digest [path]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      [digest (clj-str/replace path (str "-" digest) "")]
      [nil path])))

(defn find-asset [path]
  (let [[digest path] (remove-asset-digest path)]
    (when-let [asset (do-get path)]
      (if digest
        (when (= digest (:digest asset))
          asset)
        asset))))

(defmacro throw-unless-found [path & body]
  `(if-let [asset# ~@body]
     asset#
     (throw (Exception. (format "Asset not found: %s" ~path)))))

(defn find-asset! [path]
  (throw-unless-found path (find-asset path)))

(defn- get-path [path]
  (let [pipe (pipeline)
        prefix-fn (:path-prefixer pipe)
        path-finder-fn (:path-finder pipe)]
    (if-let [path (path-finder-fn path)]
      (prefix-fn path))))

(defn- asset-path [path]
  (throw-unless-found path (get-path path)))

(defn- build-url [path]
  ((:url-builder (pipeline)) path))

(defn asset-url [path]
  (build-url (asset-path path)))
