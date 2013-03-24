(ns conveyor.config
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :refer [split]]))

(defn- base-dir [full-path sub-path]
  (first (split full-path (re-pattern sub-path) 2)))

(defn- append-to-key [m key value]
  (update-in m [key] #(conj % value)))

(defn add-engine-config [config engine-config]
  (append-to-key config :engines engine-config))

(defn add-output-extension [config extension]
  (append-to-key config :output-extensions extension))

(defn add-input-extension [config extension]
  (append-to-key config :input-extensions extension))

(def default-engine-config
  {:input-extensions []
   :output-extensions []})

(defmacro configure-engine [& body]
  `(-> default-engine-config
     ~@body))

(defn resource-directory-path [directory-path resource-in-directory]
  (let [with-leading-slash (str "/" resource-in-directory)
        relative-path (str directory-path with-leading-slash)]
    (when-let [resource-url (resource relative-path)]
      (base-dir (str resource-url) with-leading-slash))))

(defn directory-path [path]
  (let [directory (file path)]
    (when (.exists directory)
      (.getAbsolutePath directory))))

(defn add-to-load-path [config path]
  (append-to-key config :load-paths path))

(defn add-resource-directory-to-load-path [config directory-path resource-in-directory]
  (if-let [full-path (resource-directory-path directory-path resource-in-directory)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find resource directory: " directory-path)))))

(defn add-directory-to-load-path [config path]
  (if-let [full-path (directory-path path)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find directory: " path)))))

(defn add-prefix [config prefix]
  (assoc config :prefix prefix))

(def default-pipeline-config
  {:load-paths []
   :engines []
   :prefix "/"})

(defmacro configure-asset-pipeline [& body]
  `(-> default-pipeline-config
     ~@body))
