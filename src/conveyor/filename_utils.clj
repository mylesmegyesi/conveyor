(ns conveyor.filename-utils
  (:import [org.apache.commons.io FilenameUtils]))

(defn remove-extension [file-path]
  (FilenameUtils/removeExtension file-path))

(defn add-extension [file-path extension]
  (str file-path "." extension))

(defn get-extension [file-path]
  (FilenameUtils/getExtension file-path))

(defn file-join [& paths]
  (reduce
    (fn [base-path to-add]
      (FilenameUtils/concat base-path to-add))
    paths))
