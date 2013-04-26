(ns conveyor.core
  (:require [clojure.java.io :refer [file writer copy]]
            [clojure.string :refer [replace-first] :as clj-str]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.file-utils :refer :all]
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

(defn find-asset
  ([config asset-path]
    (call-fn-for-path find-asset config asset-path))
  ([config asset-path extension]
    (let [[digest path] (remove-asset-digest asset-path extension)
          asset (get-asset (build-asset-finder config) path extension)]
    (if digest
      (when (= digest (:digest asset))
        asset)
      asset))))

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
  (if (:use-digest-path config)
    (build-path config (get-digest-path (build-asset-finder config) path extension))
    (build-path config (get-logical-path (build-asset-finder config) path extension))))

(defn- get-path! [config path extension]
  (if-let [path (get-path config path extension)]
    path
    (throw-asset-not-found path)))

(defn asset-path
  ([config path]
    (if-let [found (call-fn-for-path get-path config path)]
      found
      (throw-asset-not-found path)))
  ([config path extension]
    (if-let [found (get-path config path extension)]
      found
      (throw-asset-not-found path))))

(defn- build-url [{:keys [asset-host] :as config} path]
  (when path
    (str asset-host path)))

(defn asset-url
  ([config path]
    (build-url config (asset-path config path)))
  ([config path extension]
    (build-url config (asset-path config path extension))))

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
  (fn [{:keys [uri] :as request}]
    (let [{:keys [prefix] :as config} (get-config -config)]
      (if (.startsWith uri prefix)
        (if-let [{:keys [body logical-path]} (find-asset config (remove-prefix uri prefix))]
          {:status 200
           :headers {"Content-Length" (str (count body))
                     "Content-Type" (mime-type-of logical-path)}
           :body body}
          (handler request))
        (handler request)))))

