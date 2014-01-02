(ns conveyor.config
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :as clj-str]))

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

(defn add-resource-directory-to-load-path [config directory-path resource-in-directory]
  (if-let [full-path (resource-directory-path directory-path resource-in-directory)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find resource directory: " directory-path)))))

(defn add-directory-to-load-path [config path]
  (if-let [full-path (directory-path path)]
    (add-to-load-path config full-path)
    (throw (IllegalArgumentException. (str "Could not find directory: " path)))))

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

(defn- configure-prefix [config {:keys [prefix]}]
  (if prefix
    (add-prefix config prefix)
    config))

