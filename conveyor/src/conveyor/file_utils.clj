(ns conveyor.file-utils
  (:require [clojure.java.io :refer [file as-file input-stream as-url copy]]
            [clojure.string :refer [replace-first]]
            [conveyor.asset-body :refer [read-stream body-to-string]])
  (:import [org.apache.commons.io FilenameUtils FileUtils]
           [java.net MalformedURLException]
           [java.io FileNotFoundException FileOutputStream File]))

(defn remove-extension [file-path]
  (FilenameUtils/removeExtension file-path))

(defn add-extension [file-path extension]
  (str file-path "." extension))

(defn get-extension [file-path]
  (FilenameUtils/getExtension file-path))

(defn replace-extension [file-path extension]
  (let [path (remove-extension file-path)]
    (if (empty? extension)
      path
      (add-extension path extension))))

(defn- jar-directory? [dir]
  (try
    (= "jar" (.getProtocol (as-url dir)))
    (catch MalformedURLException e
      false)))

(defn- remove-leading-slash [path]
  (if (.startsWith path "/")
    (.substring path 1 (count path))
    path))

(defn file-join [& paths]
  (reduce
    (fn [base-path to-add]
      (FilenameUtils/concat base-path (remove-leading-slash to-add)))
    paths))

(defn make-file [path]
  (if (jar-directory? path)
    (as-url path)
    (as-file path)))

(defmulti list-files #(if (jar-directory? %) :jar :file-system))

(defn-  list-files-on-system [dir]
  (map #(.getAbsolutePath %) (FileUtils/listFiles (file dir) nil true)))

(defmethod list-files :file-system [dir]
  (list-files-on-system dir))

(defn- jar-entries [jar-path]
  (.entries (.getJarFile (.openConnection (as-url jar-path)))))

(def ^:private files-in-jar
  (memoize
    (fn [jar-path]
      (loop [entries (jar-entries jar-path) results []]
        (if (.hasMoreElements entries)
          (recur entries (conj results (.getName (.nextElement entries))))
          results)))))

(def ^:private list-files-in-jar
  (memoize
    (fn [dir]
      (let [jar-path (.substring dir 0 (+ 2 (.indexOf dir "!/")))
            without-jar-path (replace-first dir jar-path "")]
        (map
          #(file-join jar-path %)
          (filter
            #(.startsWith % without-jar-path)
            (files-in-jar jar-path)))))))

(defmethod list-files :jar [dir]
  (list-files-in-jar dir))

(defn ensure-directory [dir]
  (when-not (.exists dir)
    (FileUtils/forceMkdir dir)))

(defn ensure-directory-of-file [file-name]
  (ensure-directory (.getParentFile (file file-name))))

(defn- read-normal-file [file-path]
  (let [file (as-file file-path)]
    (when (and (.exists file) (.isFile file))
      (read-stream (input-stream file)))))

(defn- read-resource-file [file-path]
  (try
    (read-stream (.openStream (as-url file-path)))
    (catch MalformedURLException e
      nil)
    (catch FileNotFoundException e
      nil)))

(defn read-file [file-path]
  (if-let [file (read-normal-file file-path)]
    file
    (read-resource-file file-path)))

(defn body-to-stream [body out]
    (with-open [out out]
      (doseq [c (.toCharArray (body-to-string body))]
        (.write out (int c)))))

(defn write-file [f body]
  (body-to-stream body (FileOutputStream. (file f))))

(defn -build-possible-input-files [load-paths]
  (reduce
    (fn [files load-path]
      (reduce
        (fn [files file]
          (conj files
                {:absolute-path file
                 :relative-path (replace-first file (str load-path "/") "")}))
        files
        (list-files load-path)))
    []
    load-paths))

(declare ^:dynamic *file-cache*)

(defmacro with-file-cache [load-paths & body]
  `(binding [*file-cache* (-build-possible-input-files ~load-paths)]
    ~@body))

(defn build-possible-input-files [load-paths]
  (if (bound? #'*file-cache*)
    *file-cache*
    (-build-possible-input-files load-paths)))

