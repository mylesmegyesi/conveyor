(ns conveyor.compile
  (:require [clojure.string :refer [join]]
            [conveyor.asset :refer [build-asset]]
            [conveyor.context :refer [set-asset-extension]]))

(defn- throw-multiple-output-exensions-with-no-requested-output-extension [{:keys [requested-path found-path]}
                                                               output-extensions]
  (throw (Exception. (format "Search for \"%s\" found \"%s\". However, you did not request an output extension and the matched compiler has multiple output extensions: %s"
                             requested-path
                             found-path
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

(defn compile-asset [{:keys [config found-extension requested-extension requested-path] :as context}]
  (let [compilers (compilers-for-extension (:compilers config) found-extension requested-extension)
        num-compilers (count compilers)
        compiler (first compilers)
        output-extensions (:output-extensions compiler)]
    (cond
      (> num-compilers 1)
      (throw-multiple-compilers found-extension requested-extension)
      (= num-compilers 1)
      (if (empty? requested-extension)
        (if (= 1 (count output-extensions))
          (set-asset-extension context (first output-extensions))
          (throw-multiple-output-exensions-with-no-requested-output-extension
            context output-extensions))
        (set-asset-extension context requested-extension))
      :else
      (set-asset-extension context found-extension))))

