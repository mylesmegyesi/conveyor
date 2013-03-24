(ns conveyor.core
  (:require [clojure.java.io :refer [as-file input-stream as-url]]
            [clojure.string :refer [join replace-first] :as clj-str]
            [digest :refer [md5]]
            [pantomime.mime :refer [mime-type-of]]
            [conveyor.filename-utils :refer :all])
  (:import [java.net MalformedURLException]))

(defn- build-asset [requested-asset-path extension asset-body]
  (let [digest (md5 asset-body)
        file-name (remove-extension requested-asset-path)]
    [{:body asset-body
      :logical-path (add-extension file-name extension)
      :digest digest
      :digest-path (add-extension (str file-name "-" digest) extension)}]))

(defn- requested-extension-matches-output-extension [handler]
  (fn [{:keys [engine found-file-extension requested-asset-path requested-file-extension asset-body]
        :as context}]
    (if (and engine
             (some #(= requested-file-extension %) (:output-extensions engine)))
      (build-asset requested-asset-path
                   requested-file-extension
                   asset-body)
      (handler context))))

(defn- throw-multipe-output-exensions-with-no-requested-output-extension [{:keys [requested-asset-path found-file-path]}
                                                               output-extensions]
  (throw (Exception. (format "Search for \"%s\" found \"%s\". However, you did not request an output extension and the matched engine has multiple output extensions: %s"
                             requested-asset-path
                             found-file-path
                             (join ", " output-extensions)))))

(defn- no-requested-extension-and-one-output-extension [handler]
  (fn [{:keys [engine requested-asset-path requested-file-extension asset-body]
        :as context}]
    (let [output-extensions (:output-extensions engine)]
      (if (and engine (= requested-file-extension ""))
        (if (= 1 (count output-extensions))
          (build-asset requested-asset-path
                       (first output-extensions)
                       asset-body)
          (throw-multipe-output-exensions-with-no-requested-output-extension
            context output-extensions))
        (handler context)))))

(defn- no-engine-for-file [handler]
  (fn [{:keys [engine requested-asset-path requested-file-extension found-file-extension asset-body]
        :as context}]
    (if (and (nil? engine)
             (= found-file-extension requested-file-extension))
      (build-asset requested-asset-path
                   requested-file-extension
                   asset-body)
      (handler context))))

(defn- null-handler [context] nil)

(defn- engines-for-extension [engines input-extension output-extension]
  (filter
    (fn [engine]
      (and (some #(= % input-extension) (:input-extensions engine))
           (if (empty? output-extension)
             true
             (some #(= % output-extension) (:output-extensions engine)))))
    engines))

(defn- engine-for-extension [engines input-extension output-extension]
  (let [engines (engines-for-extension engines input-extension output-extension)]
    (if (> (count engines) 1)
      (throw (Exception. (format "Found multiple engines to handle input extension \"%s\" and output extension \"%s\""
                                 input-extension output-extension)))
      (first engines))))

(defn- build-serve-context [asset-body found-file-path engines requested-asset-path requested-file-extension]
  (let [found-file-extension (get-extension found-file-path)
        engine (engine-for-extension engines found-file-extension requested-file-extension)]
    {:asset-body asset-body
     :found-file-path found-file-path
     :found-file-extension found-file-extension
     :requested-asset-path requested-asset-path
     :requested-file-extension requested-file-extension
     :engine engine}))

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
    (when-let [file (read-resource-file file-path)]
      file)))

(defn- read-files [{:keys [load-paths engines]} asset-path]
  (let [extensions (distinct (mapcat :input-extensions engines))]
    (reduce
      (fn [assets file-path]
        (if-let [contents (read-file file-path)]
          (conj assets {:body contents
                        :full-path asset-path})
          (let [base-path (remove-extension file-path)]
            (reduce
              (fn [assets extension]
                (let [path (add-extension base-path extension)]
                  (if-let [contents (read-file path)]
                    (conj assets {:body contents
                                  :full-path path})
                    assets)))
              assets
              extensions))))
      []
      (map #(file-join % asset-path) load-paths))))

(defn- format-asset-path [asset-path]
  (format "\"%s\"" asset-path))

(defn- throw-multiple-found-exception [requested-asset-path found-paths]
  (throw
    (Exception.
      (format "Search for %s returned multiple results: %s"
              (format-asset-path requested-asset-path)
              (join ", " (map format-asset-path found-paths))))))

(defn- read-asset [config asset-path on-asset-read]
  (let [found-assets (read-files config asset-path)
        num-found-assets (count found-assets)]
    (cond
      (> num-found-assets 1)
      (throw-multiple-found-exception asset-path (map :full-path found-assets))
      (= num-found-assets 1)
      (let [{:keys [body full-path]} (first found-assets)]
        (on-asset-read body full-path)))))

(defn- serve-asset [config requested-asset-path requested-file-extension]
  (read-asset config requested-asset-path
    (fn [asset-body found-file-path]
      ((->
         null-handler
         no-requested-extension-and-one-output-extension
         requested-extension-matches-output-extension
         no-engine-for-file)
         (build-serve-context asset-body
                              found-file-path
                              (:engines config)
                              requested-asset-path
                              requested-file-extension)))))

(defn- remove-asset-digest [path extension]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      (let [without-match (clj-str/replace path match "")]
        (if (empty? extension)
          [digest without-match]
          [digest (str without-match "." extension)]))
      [nil path])))

(defn find-asset
  ([config path]
    (find-asset config path (get-extension path)))
  ([config path extension]
   (let [[digest path] (remove-asset-digest path extension)
         assets (serve-asset config path extension)]
     (if digest
       (when (= digest (:digest (last assets)))
         assets)
       assets))))

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

