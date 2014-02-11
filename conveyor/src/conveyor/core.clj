(ns conveyor.core
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :as clj-str]
            [conveyor.file-utils :refer [file-join]]
            [conveyor.pipeline :refer :all]
            [conveyor.strategy.interface :refer [get-asset get-logical-path get-digest-path]]
            [conveyor.strategy.runtime :refer [all-possible-output]]
            [conveyor.strategy.factory :refer [make-pipeline-strategy]]))

(defn append-to-key [m key value]
  (update-in m [key] #(conj % value)))

(defn- base-dir [full-path sub-path]
  (first (clj-str/split full-path (re-pattern sub-path) 2)))

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

(defn add-validated-resource-directory [config directory-path resource-in-directory]
  (if-let [full-path (resource-directory-path directory-path resource-in-directory)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find resource directory: " directory-path)))))

(defn add-validated-directory [config path]
  (if-let [full-path (directory-path path)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find directory: " path)))))

(defn- throw-unknown-load-path-type [type]
  (throw
    (Exception.
      (format
        "Unknown type of load-path: %s. Valid types are :resource-directory and :directory."
        type))))

(defn- configure-load-paths [{:keys [load-paths] :as config}]
  (reduce
    (fn [config {:keys [type path file-in-dir] :as load-path}]
      (cond
        (= :resource-directory type)
        (add-validated-resource-directory config path file-in-dir)
        (= :directory type)
        (add-validated-directory config path)
        (instance? String load-path)
        (add-to-load-path config load-path)
        :else
        (throw-unknown-load-path-type type)))
    (assoc config :load-paths [])
    load-paths))

(defn- configure-plugins [{:keys [plugins] :as config}]
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

(defn- apply-defaults [config]
  (merge-with #(if (nil? %2) %1 %2) default-pipeline-config config))

(defn initialize-config [config]
  (-> config
      apply-defaults
      configure-plugins
      configure-load-paths))

(defn bind-config [config pipeline f]
  (binding [*pipeline-config* config
            *pipeline* pipeline]
    (f)))

(defn- build-path-prefixer-fn [config]
  (if-let [prefix (:prefix config)]
    (fn [path] (file-join "/" prefix path))
    (fn [path] (file-join "/" path))))

(defn- build-path-finder-fn [strategy config]
  (if (:use-digest-path config)
    (fn [path] (get-digest-path strategy path))
    (fn [path] (get-logical-path strategy path))))

(defn- build-url-builder-fn [config]
  (if-let [asset-host (:asset-host config)]
    (fn [path] (str asset-host path))
    (fn [path] path)))

(defn build-pipeline [config]
  (let [strategy (make-pipeline-strategy config)]
    {:strategy strategy
     :url-builder (build-url-builder-fn config)
     :path-prefixer (build-path-prefixer-fn config)
     :path-finder (build-path-finder-fn strategy config)}))

(defn build-pipeline-bind-fn [-config]
  (let [config (initialize-config -config)
        pipeline (build-pipeline config)]
    (fn [f]
      (bind-config config pipeline f))))

(defmacro with-pipeline-config [config & body]
  "Initializes and binds the pipeline given a config, then executes body"
  `(let [config# (initialize-config ~config)]
     (bind-config config# (build-pipeline config#) (fn [] ~@body))))

(defn find-asset [path]
  "Finds an asset according to the configured strategy and returns an asset map"
  (get-asset (:strategy (pipeline)) path))

(defmacro throw-unless-found [path & body]
  `(if-let [asset# ~@body]
     asset#
     (throw (Exception. (format "Asset not found: %s" ~path)))))

(defn find-asset! [path]
  (throw-unless-found path (find-asset path)))

(defn- regex? [path]
  (= (re-pattern path) path))

(defn- find-matches [path possible-files]
  (if (regex? path)
    (reduce
      (fn [files {:keys [relative-path]}]
        (if (re-matches path relative-path)
          (conj files relative-path)
          files))
      []
      possible-files)
    [path]))

(defn find-regex-matches [paths possible-files]
  (-> (map #(find-matches % possible-files) paths)
      (flatten)
      (set)))

(defn- filter-regex [paths]
  (if (some regex? paths)
    (find-regex-matches paths (all-possible-output (pipeline-config)))
    paths))

(defn find-assets [paths]
  (let [filtered-paths (filter-regex paths)]
    (flatten (doall (map #(find-asset! %) filtered-paths)))))

(defn- asset-path [path]
  (throw-unless-found path (get-path path)))

(defn- build-url [path]
  ((:url-builder (pipeline)) path))

(defn asset-url [path]
  "If use-digest-path is true, returns the digest-path,
   else returns the logical-path of an asset"
  (build-url (asset-path path)))
