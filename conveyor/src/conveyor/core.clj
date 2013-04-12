(ns conveyor.core
  (:require [clojure.java.io :refer [file writer copy]]
            [clojure.string :refer [replace-first]]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.file-utils :refer :all]
            [conveyor.manfiest :refer [manifest-path]]
            [conveyor.dynamic-asset-finder :refer [find-asset] :rename {find-asset dynamic-find-asset}]
            [conveyor.static-asset-finder :refer [find-asset] :rename {find-asset static-find-asset}]))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

(defn- build-path [config path]
  (file-join "/" (:prefix config) path))

(defn- base-path [config asset]
  (if (:use-digest-path config)
    (:digest-path asset)
    (:logical-path asset)))

(defn- path [config asset]
  (build-path config (base-path config asset)))

(defn- paths [config assets]
  (map #(path config %) assets))

(defn- urls [{:keys [asset-host] :as config} assets]
  (map #(str asset-host (path config %)) assets))

(defn- build-asset-finder [{:keys [search-strategy] :as config}]
  (case search-strategy
    :dynamic dynamic-find-asset
    :static static-find-asset))

(defn find-asset
  ([config path]
     (find-asset config path (get-extension path)))
  ([config path extension]
    ((build-asset-finder config) config path extension)))

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

(defn- write-assets [config assets]
  (doseq [asset assets]
    (let [file-name (file-join (:output-dir config) (path config asset))]
      (ensure-directory-of-file file-name)
      (write-file file-name (:body asset))
      (write-gzipped-file file-name (:body asset)))))

(defn- build-manifest [config assets]
  (reduce
    (fn [manifest asset]
      (assoc manifest
             (:logical-path asset)
             (base-path config asset)))
    {}
    assets))

(defn- write-manifest [config assets]
  (let [manifest (manifest-path config)]
    (ensure-directory-of-file manifest)
    (spit manifest (build-manifest config assets))))

(defn precompile [config paths]
  (let [assets (mapcat #(find-asset config %) paths)]
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
        (if-let [{:keys [body logical-path]} (last (find-asset config (remove-prefix uri prefix)))]
          {:status 200
           :headers {"Content-Length" (str (count body))
                     "Content-Type" (mime-type-of logical-path)}
           :body body}
          (handler request))
        (handler request)))))

