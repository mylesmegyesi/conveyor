(ns conveyor.file-utils
  (:require [clojure.java.io :refer [file as-file input-stream as-url copy output-stream]]
            [clojure.string :refer [replace-first]])
  (:import [org.apache.commons.io FilenameUtils FileUtils]
           [java.net MalformedURLException JarURLConnection URL]
           [java.io FileNotFoundException FileOutputStream]
           [java.util.zip GZIPOutputStream]))

(defn remove-extension [file-path]
  (FilenameUtils/removeExtension file-path))

(defn add-extension [file-path extension]
  (str file-path "." extension))

(defn get-extension [file-path]
  (FilenameUtils/getExtension file-path))

(defn replace-extension [file-path extension]
  (add-extension (remove-extension file-path) extension))

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

(defn read-stream [stream]
  (let [sb (StringBuilder.)]
    (with-open [stream stream]
      (loop [c (.read stream)]
        (if (neg? c)
          (str sb)
          (do
            (.append sb (char c))
            (recur (.read stream))))))))

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

(defn- string->stream [body out]
  (with-open [out out]
    (doseq [c (.toCharArray body)]
      (.write out (int c)))))

(defn write-gzipped-file [f body]
  (let [file-name (add-extension f "gz")]
    (string->stream body (GZIPOutputStream. (FileOutputStream. (file file-name))))))

(defn write-file [f body]
  (string->stream body (FileOutputStream. (file f))))

