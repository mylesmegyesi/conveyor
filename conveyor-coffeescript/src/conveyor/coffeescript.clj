(ns conveyor.coffeescript
  (:require [clojure.java.io :refer [reader resource]]
            [conveyor.config :refer :all])
  (:import [org.mozilla.javascript Context ContextFactory]))

(defn- make-context []
  (doto (-> (ContextFactory.)
          (.enterContext))
    (.setOptimizationLevel -1)))

(declare ^:dynamic *context*)

(defn- with-context* [f]
  (binding [*context* (make-context)]
    (try
      (f)
      (finally
        (Context/exit)))))

(defmacro with-context [& body]
  `(with-context* (fn [] ~@body)))

(declare ^:dynamic *scope*)

(defn load-js-file [filename]
  (.evaluateReader *context* *scope*
                   (reader (resource filename))
                   filename 1 nil))

(defn- make-scope []
  (binding [*scope* (.initStandardObjects *context*)]
    (load-js-file "conveyor/coffeescript/coffee-script-1.6.2.js")
    (load-js-file "conveyor/coffeescript/compiler.js")
    *scope*))

(defn- with-scope* [f]
  (binding [*scope* (make-scope)]
    (f)))

(defmacro with-scope [& body]
  `(with-scope* (fn [] ~@body)))

(defn- jsobj->map [jsobj]
  (if (instance? org.mozilla.javascript.ScriptableObject jsobj)
    (into {}
          (doseq [id (.getAllIds jsobj)]
            [(keyword id) (if (instance? java.lang.String id)
                            (.get jsobj (cast java.lang.String id) jsobj)
                            (throw "try again"))]))
    jsobj))

(defmacro catch-carefully [& body]
  `(try
     ~@body
     (catch org.mozilla.javascript.JavaScriptException e#
       (let [script-trace# (.getScriptStackTrace e#)
             message# (str (-> e# .getMessage jsobj->map))
             new-exc# (java.lang.Exception. (str message# "\n" script-trace#))]
         (.setStackTrace new-exc# (.getStackTrace e#))
         (throw new-exc#)))))

(defn render-string [body & {:keys [filename]}]
  (with-context
    (with-scope
      (catch-carefully
        (let [fun (.get *scope* "compileCoffeeScript" *scope*)]
          (.call fun *context* *scope* nil (object-array [body filename])))))))

(defn- compile-coffeescript [config body file-path input-extension output-extension]
  (render-string body :filename file-path))

(defn configure-coffeescript [config]
  (add-compiler-config
    config
    (configure-compiler
      (add-input-extension "coffee")
      (add-output-extension "js")
      (set-compiler compile-coffeescript))))

