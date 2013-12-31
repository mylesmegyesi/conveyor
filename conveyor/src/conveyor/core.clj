(ns conveyor.core
  (:require [clojure.string :as clj-str]
            [clojure.java.io :refer [resource file]]
            [conveyor.file-utils :refer [file-join]]
            [conveyor.compile :refer [compile-asset]]
            [conveyor.compress :refer [compress-asset]]
            [conveyor.finder.interface :refer [get-asset get-logical-path get-digest-path]]
            [conveyor.finder.factory :refer [make-asset-finder]]))

(defn compile? [config]
  (and (:pipeline-enabled config) (:compile config)))

(defn compress? [config]
  (and (:pipeline-enabled config) (:compress config)))

(defn- base-dir [full-path sub-path]
  (first (clj-str/split full-path (re-pattern sub-path) 2)))

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

(defn add-prefix [config prefix]
  (assoc config :prefix prefix))

(defn set-use-digest-path [config value]
  (assoc config :use-digest-path value))

(defn set-output-dir [config path]
  (assoc config :output-dir path))

(defn set-asset-finder [config strat]
  (assoc config :asset-finder strat))

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

(defmacro thread-pipeline-config [& body]
  `(-> default-pipeline-config
     ~@body))

(defn- configure-prefix [config {:keys [prefix]}]
  (if prefix
    (add-prefix config prefix)
    config))

;(defn- configure-asset-host [config {:keys [asset-host]}]
;  (set-asset-host config asset-host))
;
;(defn- configure-use-digest-path [config {:keys [use-digest-path]}]
;  (set-use-digest-path config use-digest-path))
;
;(defn- configure-output-dir [config {:keys [output-dir]}]
;  (set-output-dir config (or output-dir (:output-dir config))))
;
;(defn- configure-manifest [config {:keys [manifest]}]
;  (set-manifest config manifest))
;
;(defn- configure-asset-finder [config {:keys [asset-finder]}]
;  (set-asset-finder config (or asset-finder (:asset-finder config))))
;
;(defn- configure-compression [config {:keys [compress]}]
;  (set-compression config (if (nil? compress) (:compress config) compress)))
;
;(defn- configure-compile [config {:keys [compile]}]
;  (set-compile config (if (nil? compile) (:compile config) compile)))
;
;(defn- configure-pipeline [config {:keys [pipeline-enabled]}]
;  (set-pipeline-enabled config (if (nil? pipeline-enabled) (:pipeline-enabled config) pipeline-enabled)))
;
;(defn configure-asset-pipeline [config]
;  (thread-pipeline-config
;    (configure-load-paths config)
;    (configure-prefix config)
;    (configure-plugins config)
;    (configure-asset-host config)
;    (configure-use-digest-path config)
;    (configure-output-dir config)
;    (configure-manifest config)
;    (configure-asset-finder config)
;    (configure-compression config)
;    (configure-compile config)
;    (configure-pipeline config)))

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

(defn- throw-unknown-load-path-type [type]
  (throw
    (Exception.
      (format
        "Unknown type of load-path: %s. Valid types are :resource-directory and :directory."
        type))))

(defn- configure-load-paths [{:keys [load-paths] :as config}]
  (reduce
    (fn [config {:keys [type path file-in-dir]}]
      (cond
        (= :resource-directory type)
        (add-resource-directory-to-load-path config path file-in-dir)
        (= :directory type)
        (add-directory-to-load-path config path)
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
      apply-defaults
      configure-load-paths
      configure-plugins))

(defmacro bind-config [config pipeline & body]
  `(binding [*pipeline-config* ~config
            *pipeline* ~pipeline]
    ~body))

(defmacro with-pipeline-config [config & body]
  `(let [config# ~config]
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
