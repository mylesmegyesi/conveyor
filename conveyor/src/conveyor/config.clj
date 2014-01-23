(ns conveyor.config
  (:require [clojure.java.io :refer [resource file]]
            [clojure.string :as clj-str]
            [conveyor.core :refer [add-to-load-path append-to-key]]))

(defmacro thread-pipeline-config [& body]
  "Threads an empty config through any config modifiers"
  `(-> {}
     ~@body))

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

(defn set-strategy [config strategy]
  (assoc config :strategy strategy))

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

(defn set-cache-dir [config dir]
  (assoc config :cache-dir dir))

(defn set-pipeline-enabled [config enabled]
  (assoc config :pipeline-enabled enabled))

(defn- configure-prefix [config {:keys [prefix]}]
  (if prefix
    (add-prefix config prefix)
    config))

(defn add-resource-directory-to-load-path [config directory-path resource-in-directory]
  (let [path-map {:type :resource-directory
                  :path directory-path
                  :file-in-dir resource-in-directory}]
    (add-to-load-path config path-map)))

(defn add-directory-to-load-path [config path]
  (let [path-map {:type :directory
                  :path path}]
    (add-to-load-path config path-map)))
