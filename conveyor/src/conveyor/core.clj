(ns conveyor.core
  (:require [clojure.string :as clj-str]
            [conveyor.file-utils :refer [file-join]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.compress :refer [compress-asset]]
            [conveyor.config :refer [add-resource-directory-to-load-path add-directory-to-load-path]]
            [conveyor.finder.interface :refer [get-asset get-logical-path get-digest-path]]
            [conveyor.finder.factory :refer [make-asset-finder]]))

(defn compile? [config]
  (and (:pipeline-enabled config) (:compile config)))

(defn compress? [config]
  (and (:pipeline-enabled config) (:compress config)))

(declare ^:dynamic *pipeline*)
(declare ^:dynamic *pipeline-config*)

(defn pipeline []
  (if (bound? #'*pipeline*)
    *pipeline*
    (throw (Exception. "Pipeline config not bound."))))

(defn pipeline-config []
  (if (bound? #'*pipeline-config*)
    *pipeline-config*
    (throw (Exception. "Pipeline config not bound."))))

(defmacro thread-pipeline-config [& body]
  `(-> default-pipeline-config
     ~@body))

(defn identity-pipeline-fn [config path asset]
  asset)

(defn- build-pipeline-fns [config]
  (if (:pipeline-enabled config)
    [(if (compile? config)
       compile-asset
       identity-pipeline-fn)
     (if (compress? config)
       compress-asset
       identity-pipeline-fn)]
    []))

(defn- build-pipeline-fn [config]
  (let [pipeline-fns (build-pipeline-fns config)]
    (fn [path asset]
      (reduce
        (fn [asset f]
          (f config path asset))
        asset
        pipeline-fns))))

(defn- build-path-prefixer-fn [config]
  (if-let [prefix (:prefix config)]
    (fn [path] (file-join "/" prefix path))
    (fn [path] (file-join "/" path))))

(defn build-path-finder-fn [finder config]
  (if (:use-digest-path config)
    (fn [path] (get-digest-path finder path))
    (fn [path] (get-logical-path finder path))))

(defn build-url-builder-fn [config]
  (if-let [asset-host (:asset-host config)]
    (fn [path] (str asset-host path))
    (fn [path] path)))

(defn build-pipeline [config]
  (let [finder (make-asset-finder config)]
    {:finder finder
     :pipeline-fn (build-pipeline-fn config)
     :url-builder (build-url-builder-fn config)
     :path-prefixer (build-path-prefixer-fn config)
     :path-finder (build-path-finder-fn finder config)}))

(defn- do-get [path]
  (let [{:keys [finder pipeline-fn]} (pipeline)]
    (when-let [asset (get-asset finder path)]
      (pipeline-fn path asset))))

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
        (add-resource-directory-to-load-path config path file-in-dir)
        (= :directory type)
        (add-directory-to-load-path config path)
        (not (map? load-path))
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

(def default-pipeline-config
  {:load-paths []
   :compilers []
   :compressors []
   :prefix "/"
   :output-dir "public"
   :asset-finder :load-path
   :compress false
   :compile true
   :pipeline-enabled true})

(defn apply-defaults [config]
  (merge-with #(if (nil? %2) %1 %2) default-pipeline-config config))

(defn initialize-config [config]
  (-> config
      configure-load-paths
      configure-plugins
      apply-defaults))

(defmacro bind-config [config pipeline & body]
  `(binding [*pipeline-config* ~config
            *pipeline* ~pipeline]
    ~body))

(defmacro with-pipeline-config [config & body]
  `(let [config# (initialize-config ~config)]
     (bind-config config# (build-pipeline config#) (fn [] ~@body))))

(defn- remove-asset-digest [path]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      [digest (clj-str/replace path (str "-" digest) "")]
      [nil path])))

(defn add-asset-suffix [asset suffix]
  (-> (update-in asset [:digest-path] #(str % suffix))
      (update-in [:logical-path] #(str % suffix))))

(defn wrap-suffix [handler]
  (fn [path]
    (let [suffix (re-find #"\?#.*" path)
          path (clj-str/replace path #"\?#.*" "")
          asset (handler path)]
      (when asset
        (if (map? asset)
          (add-asset-suffix asset suffix)
          (str asset suffix))))))

(defn -find-asset [path]
  (let [[digest path] (remove-asset-digest path)]
    (when-let [asset (do-get path)]
      (if digest
        (when (= digest (:digest asset))
          asset)
        asset))))

(def find-asset
  (-> -find-asset
      wrap-suffix))

(defmacro throw-unless-found [path & body]
  `(if-let [asset# ~@body]
     asset#
     (throw (Exception. (format "Asset not found: %s" ~path)))))

(defn find-asset! [path]
  (throw-unless-found path (find-asset path)))

(defn- get-path [path]
  (let [pipe (pipeline)
        prefix-fn (:path-prefixer pipe)
        path-finder-fn (:path-finder pipe)]
    (if-let [path (path-finder-fn path)]
      (prefix-fn path))))

(defn- asset-path [path]
  (throw-unless-found path (get-path path)))

(defn- build-url [path]
  ((:url-builder (pipeline)) path))

(defn -asset-url [path]
  (build-url (asset-path path)))

(def asset-url
  (-> -asset-url
      wrap-suffix))
