(ns conveyor.context
  (:require [conveyor.file-utils :refer [get-extension]]))

(defmacro make-serve-context [& forms]
  `(-> {}
    ~@forms))

(defn set-config [context config]
  (assoc context :config config))

(defn set-found-extension [context found-extension]
  (assoc context :found-extension found-extension))

(defn set-found-path [context found-path]
  (assoc context :found-path found-path))

(defn set-requested-extension [context requested-extension]
  (assoc context :requested-extension requested-extension))

(defn set-requested-path [context requested-path]
  (assoc context :requested-path requested-path))

(defn set-asset-body [context extension]
  (assoc-in context [:asset :body] extension))

(defn set-asset-extension [context extension]
  (assoc-in context [:asset :extension] extension))

(defn get-asset-extension [context]
  (:extension (:asset context)))

(defn get-asset-body [context]
  (:body (:asset context)))

(defn get-requested-path [context]
  (:requested-path context))

(defn get-requested-extension [context]
  (:requested-extension context))

(defn get-found-extension [context]
  (:found-extension context))

(defn get-found-path [context]
  (:found-path context))

(defn get-config [context]
  (:config context))
