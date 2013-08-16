(ns conveyor.middleware
  (:require [pantomime.mime :refer [mime-type-of]]
            [conveyor.core :refer [with-pipeline-config find-asset]]
            [clojure.string :refer [replace-first] :as clj-str]))

(defprotocol GetConfig
  (get-config [this]))

(extend-protocol GetConfig
  java.lang.Object
  (get-config [this] this)

  clojure.lang.Delay
  (get-config [this] @this))

(defn- remove-prefix [uri prefix]
  (let [without-prefix (replace-first uri prefix "")]
    (if (.startsWith without-prefix "/")
      (replace-first without-prefix "/" "")
      without-prefix)))

(defn wrap-asset-pipeline [handler -config]
  (fn [{:keys [uri] :as request}]
    (let [config (get-config -config)
          {:keys [prefix]} config]
      (if (.startsWith uri prefix)
        (with-pipeline-config
          config
          (if-let [{:keys [body logical-path]} (find-asset (remove-prefix uri prefix))]
            {:status 200
             :headers {"Content-Length" (str (count body))
                       "Content-Type" (mime-type-of logical-path)}
             :body body}
            (handler request)))
        (handler request)))))

