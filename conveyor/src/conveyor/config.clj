(ns conveyor.config
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :refer [split]]))

(defn compile? [config]
  (and (:pipeline-enabled config) (:compile config)))

(defn compress? [config]
  (and (:pipeline-enabled config) (:compress config)))

(defn- base-dir [full-path sub-path]
  (first (split full-path (re-pattern sub-path) 2)))

(defn- append-to-key [m key value]
  (update-in m [key] #(conj % value)))

(defn add-compiler-config [config compiler-config]
  (append-to-key config :compilers compiler-config))

(defn add-compressor-config [config compressor-config]
  (append-to-key config :compressors compressor-config))

(defn add-output-extension [config extension]
  (append-to-key config :output-extensions extension))

(defn add-input-extension [config extension]
  (append-to-key config :input-extensions extension))

(defn set-input-extension [config extension]
  (assoc config :input-extension extension))

(defn set-compiler [config compiler]
  (assoc config :compiler compiler))

(defn set-compressor [config compressor]
  (assoc config :compressor compressor))

(def default-compiler-config
  {:input-extensions []
   :output-extensions []
   :compiler (fn [config asset input-extension output-extension] asset)})

(defmacro configure-compiler [& body]
  `(-> default-compiler-config
     ~@body))

(def default-compressor-config
  {:input-extension nil
   :compressor (fn [config body filename] body)})

(defmacro configure-compressor [& body]
  `(-> default-compressor-config
     ~@body))

(defn directory-path [path]
  (let [directory (file path)]
    (when (.exists directory)
      (.getAbsolutePath directory))))

(defn- normalize-resource-url [url]
  (if (= "file" (.getProtocol url))
    (directory-path (.getPath url))
    (str url "/")))

(defn resource-directory-path [directory-path resource-in-directory]
  (let [with-leading-slash (str "/" resource-in-directory)
        relative-path (str directory-path with-leading-slash)]
    (when-let [resource-url (resource relative-path)]
      (base-dir (normalize-resource-url resource-url) with-leading-slash))))

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

(defn set-search-strategy [config strat]
  (assoc config :search-strategy strat))

(defn- normalize-asset-host [host]
  (when host
    (if (.endsWith host "/")
      (.substring host 0 (dec (count host)))
      host)))

(defn set-asset-host [config host]
  (assoc config :asset-host (normalize-asset-host host)))

(defn set-manifest [config manifest-path]
  (assoc config :manifest manifest-path))

(defn set-compression [config compression]
  (assoc config :compress compression))

(defn set-compile [config compile]
  (assoc config :compile compile))

(defn set-pipeline-enabled [config enabled]
  (assoc config :pipeline-enabled enabled))

(def default-pipeline-config
  {:load-paths []
   :compilers []
   :compressors []
   :prefix "/"
   :output-dir "public"
   :search-strategy :dynamic
   :compress false
   :compile true
   :pipeline-enabled true})

(defmacro thread-pipeline-config [& body]
  `(-> default-pipeline-config
     ~@body))

(defn- configure-plugins [config {:keys [plugins]}]
  (reduce
    (fn [config plugin]
      (let [[plugin-name options] (if (map? plugin)
                                    [(:plugin-name plugin) (dissoc plugin :plugin-name)]
                                    [plugin nil])
            ns-sym (symbol (format "conveyor.%s" (name plugin-name)))]
        (require ns-sym)
        (let [configure-sym (symbol (format "configure-%s" (name plugin-name)))
              configure-fn (ns-resolve (the-ns ns-sym) configure-sym)]
          (if options
            (configure-fn config options)
            (configure-fn config)))))
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
        "Unknown type of load-path: %s. Valid types are :resource-directory and :directory."
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

(defn- configure-search-strategy [config {:keys [search-strategy]}]
  (set-search-strategy config (or search-strategy (:search-strategy config))))

(defn- configure-compression [config {:keys [compress]}]
  (set-compression config (if (nil? compress) (:compress config) compress)))

(defn- configure-compile [config {:keys [compile]}]
  (set-compile config (if (nil? compile) (:compile config) compile)))

(defn- configure-pipeline [config {:keys [pipeline-enabled]}]
  (set-pipeline-enabled config (if (nil? pipeline-enabled) (:pipeline-enabled config) pipeline-enabled)))

(defn configure-asset-pipeline [config]
  (thread-pipeline-config
    (configure-load-paths config)
    (configure-prefix config)
    (configure-plugins config)
    (configure-asset-host config)
    (configure-use-digest-path config)
    (configure-output-dir config)
    (configure-manifest config)
    (configure-search-strategy config)
    (configure-compression config)
    (configure-compile config)
    (configure-pipeline config)))

