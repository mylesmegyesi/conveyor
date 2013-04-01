(ns conveyor.file-utils
  (:require [clojure.java.io :refer [file as-file input-stream as-url copy output-stream]])
  (:import [org.apache.commons.io FilenameUtils FileUtils]
           [java.net MalformedURLException]
           [java.io FileNotFoundException]
           [java.util.zip GZIPInputStream GZIPOutputStream]))

(defn remove-extension [file-path]
  (FilenameUtils/removeExtension file-path))

(defn add-extension [file-path extension]
  (str file-path "." extension))

(defn get-extension [file-path]
  (FilenameUtils/getExtension file-path))

(defn- remove-leading-slash [path]
  (if (.startsWith path "/")
    (.substring path 1 (count path))
    path))

(defn file-join [& paths]
  (reduce
    (fn [base-path to-add]
      (FilenameUtils/concat base-path (remove-leading-slash to-add)))
    paths))

(defn ensure-directory [dir]
  (when-not (.exists dir)
    (FileUtils/forceMkdir dir)))

(defn ensure-directory-of-file [file-name]
  (ensure-directory (.getParentFile (file file-name))))

(defn- read-stream [stream]
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

(defn gunzip [input output & opts]
  (with-open [input (-> input input-stream GZIPInputStream.)]
    (apply copy input output opts)))

(defn gzip [input output & opts]
  (with-open [output (-> output output-stream GZIPOutputStream.)]
    (apply copy input output opts)))

