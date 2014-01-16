(ns conveyor.middleware
  (:require [clojure.string :refer [replace-first] :as clj-str]
            [conveyor.asset-body :refer [content-length last-modified-date response-body]]
            [conveyor.core  :refer [bind-config build-pipeline find-asset initialize-config]]
            [conveyor.file-utils :refer [with-file-cache]]
            [conveyor.time-utils :refer [string-to-date]]
            [pantomime.mime :refer [mime-type-of]]))

(defn- build-asset-request?-fn [config]
  (fn [{:keys [uri]}] (.startsWith uri (:prefix config))))

(defn- build-prefix-remover-fn [config]
  (fn [uri]
    (let [prefix (:prefix config)
          without-prefix (replace-first uri prefix "")]
      (if (.startsWith without-prefix "/")
        (replace-first without-prefix "/" "")
        without-prefix))))

(defn etag-match? [etag digest]
  (when etag
    (= etag digest)))

(defn not-modified-since? [since last-modified]
  (when since
    (let [since-date (string-to-date since)
          last-modified-date (string-to-date last-modified)]
      (and since-date
           last-modified-date
           (.before last-modified-date since-date)))))

(defn not-modified-response [{:keys [headers]} {:keys [digest last-modified]}]
  (let [etag (headers "if-none-match")
        modified-since (headers "if-modified-since")]
    (if (or (etag-match? etag digest)
            (not-modified-since? modified-since last-modified))
      {:status 304})))

(defn ok-response [{:keys [logical-path content-length last-modified digest body]}]
  (let [response {:status 200
                  :headers {"Content-Type" (mime-type-of logical-path)
                            "Content-Length" (str content-length)
                            "ETag" digest}
                  :body (response-body body)}]
    (if last-modified
      (assoc-in response [:headers "Last-Modified"] last-modified)
      response)))

(defn- asset-response-fn [config]
  (let [remove-prefix (build-prefix-remover-fn config)]
    (fn [{:keys [uri] :as request}]
      (when-let [asset (find-asset (remove-prefix uri))]
        (or (not-modified-response request asset)
            (ok-response asset))))))

(defn- build-serve-asset-fn [config]
  (let [pipeline (delay (build-pipeline config))
        asset-request? (build-asset-request?-fn config)
        build-asset-response (asset-response-fn config)]
    (fn [request]
      (when (asset-request? request)
        (build-asset-response request)))))

(defn- wrap-serve-asset [handler config]
  (let [serve-asset (build-serve-asset-fn config)]
    (fn [request]
      (or (serve-asset request) (handler request)))))

(defn- wrap-bind-config [handler config pipeline]
  (fn [request]
    (bind-config
      config
      pipeline
      (fn []
        (with-file-cache (:load-paths config)
          (handler request))))))

(defn wrap-pipeline-config [handler -config]
  (let [config (initialize-config -config)]
    (wrap-bind-config
      handler
      config
      (build-pipeline config))))

(defn wrap-asset-pipeline [handler -config]
  (let [config (initialize-config -config)]
    (-> handler
      (wrap-serve-asset config)
      (wrap-bind-config config (build-pipeline config)))))
