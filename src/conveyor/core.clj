(ns conveyor.core
  (:require [clojure.java.io :refer [as-file input-stream as-url]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.filename-utils :refer :all]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.context :refer :all]
            [conveyor.asset :refer [build-asset]])
  (:import [java.net MalformedURLException]))

(defn- read-stream [stream]
  (let [sb (StringBuilder.)]
    (with-open [stream stream]
      (loop [c (.read stream)]
        (if (neg? c)
          (str sb)
          (do
            (.append sb (char c))
            (recur (.read stream))))))))

(defn- read-normal-file [file-path]
  (let [file (as-file file-path)]
    (when (and (.exists file) (.isFile file))
      (read-stream (input-stream file)))))

(defn- read-resource-file [file-path]
  (try
    (read-stream (.openStream (as-url file-path)))
    (catch MalformedURLException e
      nil)))

(defn- read-file [file-path]
  (if-let [file (read-normal-file file-path)]
    file
    (read-resource-file file-path)))

(defn- read-files [file-paths]
  (reduce
    (fn [files path]
      (if-let [contents (read-file path)]
        (conj files {:body contents
                     :full-path path})
        files))
    []
    file-paths))

(defn- format-asset-path [asset-path]
  (format "\"%s\"" asset-path))

(defn- throw-multiple-found-exception [requested-path found-paths]
  (throw
    (Exception.
      (format "Search for %s returned multiple results: %s"
              (format-asset-path requested-path)
              (join ", " (map format-asset-path found-paths))))))

(defn- files-with-compiler-extensions [paths file-path extensions]
  (let [base-path (remove-extension file-path)]
    (reduce
      (fn [paths extension]
        (conj paths (add-extension base-path extension)))
      paths
      extensions)))

(defn- index-file [paths file-path extensions]
  (if (empty? (get-extension file-path))
    (files-with-compiler-extensions paths (str file-path "/index") extensions)
    paths))

(defn requested-file [paths file-path requested-extension]
  (if (empty? (get-extension file-path))
    (if (empty? requested-extension)
      paths
      (conj paths (add-extension "." requested-extension)))
    (conj paths file-path)))

(defn- extensions-from-compilers [compilers]
  (distinct
    (concat
      (mapcat :input-extensions compilers)
      (mapcat :output-extensions compilers))))

(defn- extensions-for-path [path compilers requested-extension]
  (extensions-from-compilers
    (if (empty? requested-extension)
      compilers
      (filter
        (fn [compiler]
          (some
            #(= % requested-extension)
            (:output-extensions compiler)))
        compilers))))

(defn- build-possible-files [context]
  (let [asset-path (get-requested-path context)
        extension (get-requested-extension context)
        {:keys [load-paths compilers]} (get-config context)
        extensions (extensions-for-path asset-path compilers extension)]
    (distinct
      (reduce
        (fn [paths file-path]
          (-> paths
            (requested-file file-path extension)
            (files-with-compiler-extensions file-path extensions)
            (index-file file-path extensions)))
        []
        (map #(file-join % asset-path) load-paths)))))

(defn- read-asset [context on-asset-read]
  (let [files-to-read (build-possible-files context)
        found-assets (read-files files-to-read)
        num-found-assets (count found-assets)]
    (cond
      (> num-found-assets 1)
      (throw-multiple-found-exception
        (get-requested-path context)
        (map :full-path found-assets))
      (= num-found-assets 1)
      (let [{:keys [body full-path]} (first found-assets)]
        (on-asset-read
          (-> context
            (set-asset-body body)
            (set-found-path full-path)
            (set-found-extension (get-extension full-path))))))))

(defn- serve-asset [context]
  (read-asset
    context
    (fn [context]
      (-> context
        compile-asset
        (as-> result-context
              (build-asset
                (get-requested-path result-context)
                (get-asset-extension result-context)
                (get-asset-body result-context)))))))

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
            assets (serve-asset (make-serve-context
                                  (set-config config)
                                  (set-requested-path path)
                                  (set-requested-extension extension)))]
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

