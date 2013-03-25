(ns conveyor.compile
  (:require [conveyor.asset :refer [build-asset]]
            [clojure.string :refer [join]]))

(defn- throw-multiple-output-exensions-with-no-requested-output-extension [{:keys [requested-file-path found-file-path]}
                                                               output-extensions]
  (throw (Exception. (format "Search for \"%s\" found \"%s\". However, you did not request an output extension and the matched compiler has multiple output extensions: %s"
                             requested-file-path
                             found-file-path
                             (join ", " output-extensions)))))

(defn- throw-multiple-compilers [input-extension output-extension]
  (throw (Exception. (format "Found multiple compilers to handle input extension \"%s\" and output extension \"%s\""
                             input-extension output-extension))))

(defn- compilers-for-extension [compilers input-extension output-extension]
  (filter
    (fn [compiler]
      (and (some #(= % input-extension) (:input-extensions compiler))
           (if (empty? output-extension)
             true
             (some #(= % output-extension) (:output-extensions compiler)))))
    compilers))

(defn compile-asset [handler]
  (fn [{:keys [config found-file-extension requested-file-extension requested-file-path asset-body] :as context}]
    (let [compilers (compilers-for-extension (:compilers config) found-file-extension requested-file-extension)
          num-compilers (count compilers)
          compiler (first compilers)
          output-extensions (:output-extensions compiler)]
    (cond
      (> num-compilers 1)
      (throw-multiple-compilers found-file-extension requested-file-extension)
      (= num-compilers 1)
      (if (empty? requested-file-extension)
        (if (= 1 (count output-extensions))
          (build-asset requested-file-path
                       (first output-extensions)
                       asset-body)
          (throw-multiple-output-exensions-with-no-requested-output-extension
            context output-extensions))
        (build-asset requested-file-path
                     requested-file-extension
                     asset-body))
      :else
      (build-asset requested-file-path
                   found-file-extension
                   asset-body)))))

