(ns conveyor.core
  (:require [clojure.string :refer [replace-first]]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.filename-utils :refer [get-extension file-join]]
            [conveyor.dynamic-asset-finder :refer [find-asset] :rename {find-asset dynamic-find-asset}]))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

(defn- path [config asset]
  (if (:use-digest-path config)
    (file-join "/" (:prefix config) (:digest-path asset))
    (file-join "/" (:prefix config) (:logical-path asset))))

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

