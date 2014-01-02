(ns conveyor.coffeescript
  (:require [clojure.java.io :refer [reader resource]]
            [conveyor.config :refer :all]
            [cheshire.core :refer [generate-string]])
  (:import [org.mozilla.javascript ContextFactory JavaScriptException]))

(defn- make-context []
  (doto (-> (ContextFactory.)
          (.enterContext))
    (.setOptimizationLevel -1)))

(def ^:private context (delay (make-context)))

(defn- make-scope []
  (.initStandardObjects @context))

(def ^:private scope (delay (make-scope)))

(defmacro catch-carefully [filename & body]
  `(try
     ~@body
     (catch JavaScriptException e#
       (let [script-trace# (.getScriptStackTrace e#)
             new-exc# (java.lang.Exception. (str ~filename ": " (.getMessage e#) "\n" script-trace#))]
         (.setStackTrace new-exc# (.getStackTrace e#))
         (throw new-exc#)))))

(defn eval-js
  ([js]
   (eval-js js ""))
  ([js filename]
   (eval-js js filename 1))
  ([js filename lineno]
   (catch-carefully filename
     (. @context evaluateString @scope js filename lineno nil))))

(defn load-js-file [filename]
  (catch-carefully filename
    (.evaluateReader @context @scope
                     (reader (resource filename))
                     filename 1 nil)))

(defn render-string [body & {:keys [filename bare header]}]
  (eval-js (format "CoffeeScript.compile(%s, %s)"
                   (generate-string body)
                   (generate-string {:filename filename
                                     :bare bare
                                     :header header}))
           filename))

(defn- compile-coffeescript [config {:keys [body absolute-path] :as asset} input-extension output-extension]
  (assoc asset :body (render-string body :filename absolute-path :bare false :header false)))

(defn- init-coffeescript []
  (load-js-file "conveyor/coffeescript/coffee-script-1.6.2.js"))

(defn configure-coffeescript [config]
  (init-coffeescript)
  (add-compiler-config
    config
    (configure-compiler
      (add-input-extension "coffee")
      (add-output-extension "js")
      (set-compiler compile-coffeescript))))
