(ns conveyor.config
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :refer [split]]))

(defn- base-dir [full-path sub-path]
  (first (split full-path (re-pattern sub-path) 2)))

(defn- append-to-key [m key value]
  (update-in m [key] #(conj % value)))

(defn add-compiler-config [config compiler-config]
  (append-to-key config :compilers compiler-config))

(defn add-output-extension [config extension]
  (append-to-key config :output-extensions extension))

(defn add-input-extension [config extension]
  (append-to-key config :input-extensions extension))

(defn set-compiler [config compiler]
  (assoc config :compiler compiler))

(def default-compiler-config
  {:input-extensions []
   :output-extensions []
   :compiler (fn [config body file-path input-extension output-extension] body)})

(defmacro configure-compiler [& body]
  `(-> default-compiler-config
     ~@body))

(defn- normalize-resource-url [url]
  (if (= "file" (.getProtocol url))
    (.getPath url)
    (str url)))

(defn resource-directory-path [directory-path resource-in-directory]
  (let [with-leading-slash (str "/" resource-in-directory)
        relative-path (str directory-path with-leading-slash)]
    (when-let [resource-url (resource relative-path)]
      (base-dir (normalize-resource-url resource-url) with-leading-slash))))

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

(defn set-use-digest-path [config value]
  (assoc config :use-digest-path value))

(defn set-output-dir [config path]
  (assoc config :output-dir path))

(defn- normalize-asset-host [host]
  (when host
    (if (.endsWith host "/")
      (.substring host 0 (dec (count host)))
      host)))

(defn set-asset-host [config host]
  (assoc config :asset-host (normalize-asset-host host)))

(defn set-manifest [config manfiest-path]
  (assoc config :manifest manfiest-path))

(def default-pipeline-config
  {:load-paths []
   :compilers []
   :prefix "/"
   :output-dir "public"})

(defmacro thread-pipeline-config [& body]
  `(-> default-pipeline-config
     ~@body))

(defn- configure-plugins [config {:keys [plugins]}]
  (reduce
    (fn [config plugin-name]
      (let [ns-sym (symbol (format "conveyor.%s" (name plugin-name)))]
        (require ns-sym)
        (let [configure-sym (symbol (format "configure-%s" (name plugin-name)))
              configure-fn (ns-resolve (the-ns ns-sym) configure-sym)]
          (configure-fn config))))
    config
    plugins))

(defn- configure-prefix [config {:keys [prefix]}]
  (if prefix
    (add-prefix config prefix)
    config))

(defn- throw-unknown-load-path-type [type]
  (throw
    (Exception.
      (format
        "Unknown type of load-path: :unknown-type. Valid types are :resource-directory and :directory."
        type))))

(defn- configure-load-paths [config {:keys [load-paths]}]
  (reduce
    (fn [config {:keys [type path file-in-dir]}]
      (cond
        (= :resource-directory type)
        (add-resource-directory-to-load-path config path file-in-dir)
        (= :directory type)
        (add-directory-to-load-path config path)
        :else
        (throw-unknown-load-path-type type)))
    config
    load-paths))

(defn- configure-asset-host [config {:keys [asset-host]}]
  (set-asset-host config asset-host))

(defn- configure-use-digest-path [config {:keys [use-digest-path]}]
  (set-use-digest-path config use-digest-path))

(defn- configure-output-dir [config {:keys [output-dir]}]
  (set-output-dir config (or output-dir (:output-dir config))))

(defn- configure-manifest [config {:keys [manifest]}]
  (set-manifest config manifest))

(defn configure-asset-pipeline [config]
  (thread-pipeline-config
    (configure-load-paths config)
    (configure-prefix config)
    (configure-plugins config)
    (configure-asset-host config)
    (configure-use-digest-path config)
    (configure-output-dir config)
    (configure-manifest config)))

