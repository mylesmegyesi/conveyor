(ns conveyor.middleware
  (:require [clojure.string :refer [replace-first] :as clj-str]
            [conveyor.core  :refer [bind-config build-pipeline find-asset initialize-config]]
            [conveyor.file-utils :refer [with-file-cache]]
            [pantomime.mime :refer [mime-type-of]]))

(defn- build-asset-request?-fn [config]
  (fn [uri] (.startsWith uri (:prefix config))))

(defn- build-prefix-remover-fn [config]
  (fn [uri]
    (let [prefix (:prefix config)
          without-prefix (replace-first uri prefix "")]
      (if (.startsWith without-prefix "/")
        (replace-first without-prefix "/" "")
        without-prefix))))

(defn- asset-response-fn [config]
  (let [remove-prefix (build-prefix-remover-fn config)]
    (fn [uri]
      (when-let [{:keys [body logical-path]} (find-asset (remove-prefix uri))]
        {:status 200
         :body body}))))

(defn- build-serve-asset-fn [config]
  (let [pipeline (delay (build-pipeline config))
        asset-request? (build-asset-request?-fn config)
        build-asset-response (asset-response-fn config)]
    (fn [uri]
      (when (asset-request? uri)
        (build-asset-response uri)))))

(defn- wrap-serve-asset [handler config]
  (let [serve-asset (build-serve-asset-fn config)]
    (fn [{:keys [uri] :as request}]
      (or (serve-asset uri) (handler request)))))

(defn- wrap-bind-config [handler config pipeline]
  (fn [request]
    (bind-config
      config
      pipeline
      (fn [] (handler request)))))

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
