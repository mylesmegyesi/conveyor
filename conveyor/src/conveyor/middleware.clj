(ns conveyor.middleware
  (:require [pantomime.mime :refer [mime-type-of]]
            [conveyor.core :refer [bind-config build-pipeline find-asset initialize-config]]
            [clojure.string :refer [replace-first] :as clj-str]))

(defprotocol GetConfig
  (get-config [this]))

(extend-protocol GetConfig
  java.lang.Object
  (get-config [this] (initialize-config this))

  clojure.lang.Delay
  (get-config [this] (initialize-config @this)))

(defn- build-asset-request?-fn [config]
  (fn [uri] (.startsWith uri (:prefix (get-config config)))))

(defn- build-prefix-remover-fn [config]
  (fn [uri]
    (let [prefix (:prefix (get-config config))
          without-prefix (replace-first uri prefix "")]
      (if (.startsWith without-prefix "/")
        (replace-first without-prefix "/" "")
        without-prefix))))

(defn- asset-response-fn [config]
  (let [remove-prefix (build-prefix-remover-fn config)]
    (fn [uri]
      (when-let [{:keys [body logical-path]} (find-asset (remove-prefix uri))]
        {:status 200
         :headers {"Content-Length" (str (count body))
                   "Content-Type" (mime-type-of logical-path)}
         :body body}))))

(defn- build-serve-asset-fn [config]
  (let [pipeline (delay (build-pipeline (get-config config)))
        asset-request? (build-asset-request?-fn config)
        build-asset-response (asset-response-fn config)]
    (fn [uri]
      (when (asset-request? uri)
        (build-asset-response uri)))))

(defn- wrap-serve-asset [handler config]
  (let [serve-asset (build-serve-asset-fn config)]
    (fn [{:keys [uri] :as request}]
      (or (serve-asset uri) (handler request)))))

(defn wrap-pipeline-config [handler config]
  (let [pipeline (delay (build-pipeline (get-config config)))]
    (fn [request]
      (bind-config
        (get-config config)
        @pipeline
        (fn [] (handler request))))))

(defn wrap-asset-pipeline [handler -config]
  (-> handler
    (wrap-serve-asset -config)
    (wrap-pipeline-config -config)))
