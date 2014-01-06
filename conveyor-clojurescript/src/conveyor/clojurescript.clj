(ns conveyor.clojurescript
  (:require [cljs.closure        :refer [build]]
            [clojure.java.io     :refer [file]]
            [conveyor.core       :refer :all]
            [conveyor.config     :refer :all]
            [conveyor.file-utils :refer [ensure-directory file-join]])
  (:import [java.net URL URLClassLoader MalformedURLException]))

(defn- as-url [path]
  (try
    (URL. path)
    (catch MalformedURLException e
      (URL. (str "file:" path "/")))))

(defn- with-load-path-on-classpath [load-paths f]
  (let [thread (Thread/currentThread)
        current-loader (.getContextClassLoader thread)
        urls (map as-url load-paths)
        new-loader (URLClassLoader. (into-array java.net.URL urls) current-loader)
        _ (.setContextClassLoader thread new-loader)
        result (f)]
    (.setContextClassLoader thread current-loader)
    result))

(defn- clojurescript-output-dir [{:keys [cache-dir] :as config}]
  (file-join cache-dir "clojurescript"))

(defn- compiler-for-options [compiler-options]
  (fn [config asset input-extension output-extension]
    (let [compiler-options (-> compiler-options
                             (assoc :output-dir (clojurescript-output-dir config))
                             (dissoc :output-to))]
      (with-load-path-on-classpath
        (:load-paths config)
        (fn []
          (assoc asset :body (build (:absolute-path asset) compiler-options)))))))

(defn- ensure-cache-dir [config]
  config)

(defn configure-clojurescript
  ([config] (configure-clojurescript config {:optimizations :whitespace}))
  ([config compiler-options]
   (ensure-directory (file (clojurescript-output-dir config)))
   (add-compiler-config
     config
     (configure-compiler
       (add-input-extension "cljs")
       (add-output-extension "js")
       (set-compiler (compiler-for-options compiler-options))))))
