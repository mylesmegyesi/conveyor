(ns conveyor.clojurescript
  (:require [conveyor.config :refer :all]
            [cljs.closure :refer [build]])
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

(defn- compiler-for-options [options]
  (fn [config asset input-extension output-extension]
    (with-load-path-on-classpath
      (:load-paths config)
      (fn [] (assoc asset :body (build (:absolute-path asset) options))))))

(defn configure-coffeescript
  ([config] (configure-coffeescript config {:optimizations :advanced}))
  ([config options]
   (add-compiler-config
     config
     (configure-compiler
       (add-input-extension "cljs")
       (add-output-extension "js")
       (set-compiler (compiler-for-options options))))))
