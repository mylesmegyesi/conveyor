(ns conveyor.file-utils
  (:require [clojure.java.io :refer [file as-file input-stream as-url copy]]
            [clojure.string :refer [split replace-first]]
            [cemerick.pomegranate :refer [get-classpath]]
            [conveyor.asset-body :refer [read-stream body-to-string]])
  (:import [org.apache.commons.io FilenameUtils FileUtils]
           [java.net URLClassLoader URL MalformedURLException]
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

(defn jar-directory? [dir]
  (try
    (= "jar" (.getProtocol (as-url dir)))
    (catch MalformedURLException e
      false)))

(defn- remove-leading-slash [path]
  (if (.startsWith path "/")
    (.substring path 1 (count path))
    path))

(defn- add-trailing-slash [path]
  (if (.endsWith path "/")
    path
    (str path "/")))

(defn file-join [& paths]
  (reduce
    (fn [base-path to-add]
      (FilenameUtils/concat base-path (remove-leading-slash to-add)))
    paths))

(defn make-file [path]
  (if (jar-directory? path)
    (as-url path)
    (as-file path)))

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

(defn- jar-entries [jar-url]
  (.entries (.getJarFile (.openConnection jar-url))))

(def ^:private files-in-jar
  (memoize
    (fn [jar-path]
      (loop [entries (jar-entries (as-url jar-path)) results []]
        (if (.hasMoreElements entries)
          (recur entries (conj results (str jar-path (.getName (.nextElement entries)))))
          results)))))

(defn- file-in-jar-with-base-path? [jar-path base-path]
  (some
    #(.startsWith % (str jar-path base-path))
    (files-in-jar jar-path)))

(defn- search-jar [base-url path]
  (let [jar-url (str (URL. "jar" "" (str base-url "!/")))]
    (if (file-in-jar-with-base-path? jar-url path)
      (add-trailing-slash (str jar-url path)))))

(defn- search-fs [base-path path]
  (let [f (file (str base-path "/" path))]
    (when (and (.exists f) (.isDirectory f))
      (add-trailing-slash (str (.toURI f))))))

(defn- search-in-path [base-url path]
  (let [base-path (.getPath base-url)]
    (if (.endsWith base-path ".jar")
      (search-jar base-url path)
      (search-fs base-path path))))

(defn- search-class-path [path]
  (->> (get-classpath)
    (map as-url)
    (map #(search-in-path % path))))

(defn- search-working-directory [path]
  (let [f (file path)]
    (when (and (.exists f) (.isDirectory f))
      (str (.toURI f)))))

(defn- absolute-path-with-protocol? [path]
  (.startsWith path "jar:"))

(defn normalize-path [path]
  (if (.startsWith path "file:")
    (replace-first path "file:" "")
    path))

(defn absolute-paths-with-protocol [path]
  (let [normalized (normalize-path path)]
    (if (absolute-path-with-protocol? normalized)
      [normalized]
      (keep identity
            (cons (search-working-directory normalized)
                  (search-class-path normalized))))))

(def class-loader-for-load-paths
  (memoize
    (fn [load-paths]
      (let [absolute-paths (mapcat absolute-paths-with-protocol (set load-paths))
            urls (set (map as-url absolute-paths))]
        (URLClassLoader. (into-array URL urls))))))

(defn- absolute-and-relative-paths [base-path absolute-path]
  {:absolute-path absolute-path
   :relative-path (last (split absolute-path (re-pattern base-path)))})

(defmulti list-files-on-url #(if (jar-directory? %) :jar :file-system))

(defn- files-in-jar-with-base-path [jar-path base-path]
  (filter
    #(.startsWith % (str jar-path base-path))
    (files-in-jar jar-path)))

(defn- split-jar-and-path [jar-path]
  (let [end-of-jar (+ 2 (.indexOf jar-path "!/"))]
    [(.substring jar-path 0 end-of-jar)
     (.substring jar-path end-of-jar (.length jar-path))]))

(defmethod list-files-on-url :jar [url]
  (let [[jar-path base-path] (split-jar-and-path (str url))]
    (map
      (partial absolute-and-relative-paths base-path)
      (files-in-jar-with-base-path jar-path base-path))))

(defmethod list-files-on-url :file-system [url]
  (let [base-path (.getPath url)]
    (map
      (partial absolute-and-relative-paths base-path)
      (keep
        (fn [f]
          (if (.isFile f)
            (.getAbsolutePath f)
            nil))
        (-> base-path file file-seq)))))

(defn list-files [load-paths]
  (let [class-loader (class-loader-for-load-paths load-paths)
        urls (.getURLs class-loader)]
    (mapcat list-files-on-url urls)))

(declare ^:dynamic *file-cache*)

(defmacro with-file-cache [load-paths & body]
  `(binding [*file-cache* (list-files ~load-paths)]
    ~@body))

(defn build-possible-input-files [load-paths]
  (if (bound? #'*file-cache*)
    *file-cache*
    (list-files load-paths)))

