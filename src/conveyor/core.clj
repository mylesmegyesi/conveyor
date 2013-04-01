(ns conveyor.core
  (:require [clojure.java.io :refer [file]]
            [clojure.string :refer [replace-first]]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.file-utils :refer [get-extension file-join ensure-directory-of-file gzip]]
            [conveyor.dynamic-asset-finder :refer [find-asset] :rename {find-asset dynamic-find-asset}])
  (:import [java.io FileOutputStream ByteArrayInputStream]))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

(defn- build-path [config path]
  (file-join "/" (:prefix config) path))

(defn- path [config asset]
  (if (:use-digest-path config)
    (build-path config (:digest-path asset))
    (build-path config (:logical-path asset))))

(defn- paths [config assets]
  (map #(path config %) assets))

(defn- urls [{:keys [asset-host] :as config} assets]
  (map #(str asset-host (path config %)) assets))

(defn find-asset
  ([config path]
     (find-asset config path (get-extension path)))
  ([config path extension]
    (dynamic-find-asset config path extension)))

(defn asset-path
  ([config path]
    (paths config (find-asset config path)))
  ([config path extension]
    (paths config (find-asset config path extension))))

(defn asset-url
  ([config path]
    (urls config (find-asset config path)))
  ([config path extension]
    (urls config (find-asset config path extension))))

(defn- write-gzipped-file [file-name body]
  (let [output-file-name (str file-name ".gz")
        in (ByteArrayInputStream. (.getBytes body))
        out (FileOutputStream. (file output-file-name))]
    (gzip in out)))

(defn- write-assets [config assets]
  (doseq [asset assets]
    (let [file-name (file-join (:output-dir config) (path config asset))]
      (ensure-directory-of-file file-name)
      (spit file-name (:body asset))
      (write-gzipped-file file-name (:body asset)))))

(defn- build-manifest [config assets]
  (reduce
    (fn [manifest asset]
      (assoc manifest
             (:logical-path asset)
             (path config asset)))
    {}
    assets))

(defn- manifest-path [{:keys [manifest output-dir prefix]}]
  (if manifest
    manifest
    (file-join output-dir prefix "manifest.edn")))

(defn- write-manifest [config assets]
  (let [manifest (manifest-path config)]
    (ensure-directory-of-file manifest)
    (spit manifest (build-manifest config assets))))

(defn precompile [config paths]
  (let [assets (mapcat #(find-asset config %) paths)]
    (write-assets config assets)
    (write-manifest config assets)))

(defn wrap-asset-pipeline [handler {:keys [prefix] :as config}]
  (fn [{:keys [uri] :as request}]
    (if (.startsWith uri prefix)
      (if-let [{:keys [body logical-path]} (last (find-asset config (remove-prefix uri prefix)))]
        {:status 200
         :headers {"Content-Length" (str (count body))
                   "Content-Type" (mime-type-of logical-path)}
         :body body}
        (handler request))
      (handler request))))

