(ns conveyor.core
  (:require [clojure.string :refer [join replace-first] :as clj-str]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.filename-utils :refer [get-extension]]
            [conveyor.dynamic-asset-finder :refer [find-asset] :rename {find-asset dynamic-find-asset}]))

(defn- remove-asset-digest [path extension]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      (let [without-match (clj-str/replace path match "")]
        (if (empty? extension)
          [digest without-match]
          [digest (str without-match "." extension)]))
      [nil path])))

(defn- throw-extension-does-not-match [path extension]
  (throw
    (Exception.
      (format
        "The extension of the asset \"%s\" does not match the requested output extension, \"%s\""
        path extension))))

(defn- extension-matches? [path extension]
  (let [file-extension (get-extension path)]
    (if (empty? file-extension)
      true
      (= file-extension extension))))

(defn find-asset
  ([config path]
     (find-asset config path (get-extension path)))
  ([config path extension]
    (if (extension-matches? path extension)
      (let [[digest path] (remove-asset-digest path extension)
            assets (dynamic-find-asset config path extension)]
        (if digest
          (when (= digest (:digest (last assets)))
            assets)
          assets))
      (throw-extension-does-not-match path extension))))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

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

