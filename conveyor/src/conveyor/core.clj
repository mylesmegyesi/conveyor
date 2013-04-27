(ns conveyor.core
  (:require [clojure.java.io :refer [file writer copy]]
            [clojure.string :refer [replace-first] :as clj-str]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.file-utils :refer :all]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.config :refer [compile?]]
            [conveyor.manfiest :refer [manifest-path]]
            [conveyor.finder.interface :refer [get-asset get-logical-path get-digest-path]]
            [conveyor.finder.factory :refer [make-asset-finder]]))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

(defn- build-asset-finder [{:keys [search-strategy] :as config}]
  (make-asset-finder search-strategy config))

(defn- remove-asset-digest [path extension]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      (let [without-match (clj-str/replace path match "")]
        (if (empty? extension)
          [digest without-match]
          [digest (str without-match "." extension)]))
      [nil path])))

(defn- call-fn-for-path [f config asset-path]
  (let [output-extension (get-extension asset-path)]
    (if-let [found (f config asset-path output-extension)]
      found
      (if-let [found (f config asset-path "")]
        found
        (f config (remove-extension asset-path) output-extension)))))

(defn identity-pipeline-fn [config path extension asset]
  asset)

(defn- build-pipeline-fns [config]
  (if (:pipeline-enabled config)
    [(if (compile? config)
       compile-asset
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

(declare ^:dynamic *pipeline*)

(defn build-pipeline [config]
  {:finder (build-asset-finder config)
   :pipeline-fn (build-pipeline-fn config)})

(defn pipeline []
  *pipeline*)

(defn- do-get [config path extension]
  (let [{:keys [finder pipeline-fn]} (pipeline)]
    (when-let [asset (get-asset finder path extension)]
      (pipeline-fn path extension asset))))

(defn- normalize-paths [asset]
  (if (:extension asset)
    (-> asset
      (assoc :logical-path (add-extension
                             (:logical-path asset)
                             (:extension asset)))
      (assoc :digest-path (add-extension
                            (:digest-path asset)
                            (:extension asset))))
    asset))

(defmacro with-pipeline [config & body]
  `(if (bound? #'*pipeline*)
     ~@body
     (binding [*pipeline* (build-pipeline ~config)]
       ~@body)))

(defn find-asset
  ([config asset-path]
    (with-pipeline config
      (call-fn-for-path find-asset config asset-path)))
  ([config asset-path extension]
    (with-pipeline config
      (let [[digest path] (remove-asset-digest asset-path extension)]
        (when-let [asset (do-get config path extension)]
          (if digest
            (when (= digest (:digest asset))
              (normalize-paths asset))
            (normalize-paths asset)))))))

(defn- throw-asset-not-found [path]
  (throw (Exception. (format "Asset not found: %s" path))))

(defn find-asset!
  ([config path]
    (if-let [asset (find-asset config path)]
      asset
      (throw-asset-not-found path)))
  ([config path extension]
    (if-let [asset (find-asset config path extension)]
      asset
      (throw-asset-not-found path))))

(defn- build-path [config path]
  (when path
    (file-join "/" (:prefix config) path)))

(defn- get-path [config path extension]
  (let [{:keys [finder]} (pipeline)]
    (if (:use-digest-path config)
      (build-path config (get-digest-path finder path extension))
      (build-path config (get-logical-path finder path extension)))))

(defn- get-path! [config path extension]
  (if-let [path (get-path config path extension)]
    path
    (throw-asset-not-found path)))

(defn asset-path
  ([config path]
    (with-pipeline config
      (if-let [found (call-fn-for-path get-path config path)]
        found
        (throw-asset-not-found path))))
  ([config path extension]
    (with-pipeline config
      (if-let [found (get-path config path extension)]
        found
        (throw-asset-not-found path)))))

(defn- build-url [{:keys [asset-host] :as config} path]
  (when path
    (str asset-host path)))

(defn asset-url
  ([config path]
    (with-pipeline config
      (build-url config (asset-path config path))))
  ([config path extension]
    (with-pipeline config
      (build-url config (asset-path config path extension)))))

(defn- write-asset-path [config asset path]
  (let [file-name (file-join (:output-dir config) path)]
    (ensure-directory-of-file file-name)
    (write-file file-name (:body asset))
    (write-gzipped-file file-name (:body asset))))

(defn- write-assets [config assets]
  (doseq [asset assets]
    (write-asset-path config asset (build-path config (:logical-path asset)))
    (write-asset-path config asset (build-path config (:digest-path asset)))))

(defn- build-manifest [config assets]
  (reduce
    (fn [manifest asset]
      (assoc manifest
             (:logical-path asset)
             {:logical-path (:logical-path asset)
              :digest-path (:digest-path asset)
              :digest (:digest asset)}))
    {}
    assets))

(defn- write-manifest [config assets]
  (let [manifest (manifest-path config)]
    (ensure-directory-of-file manifest)
    (spit manifest (build-manifest config assets))))

(defn precompile [config paths]
  (let [assets (map #(find-asset! config %) paths)]
    (write-assets config assets)
    (write-manifest config assets)))

(defprotocol GetConfig
  (get-config [this]))

(extend-protocol GetConfig
  java.lang.Object
  (get-config [this] this)

  clojure.lang.Delay
  (get-config [this] @this))

(defn wrap-asset-pipeline [handler -config]
  (let [config (get-config -config)
        pipe (build-pipeline config)]
    (fn [{:keys [uri] :as request}]
      (let [{:keys [prefix]} config]
        (if (.startsWith uri prefix)
          (binding [*pipeline* pipe]
            (if-let [{:keys [body logical-path]} (find-asset config (remove-prefix uri prefix))]
              {:status 200
               :headers {"Content-Length" (str (count body))
                         "Content-Type" (mime-type-of logical-path)}
               :body body}
              (handler request)))
            (handler request))))))

