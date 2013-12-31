(ns conveyor.closure
  (:require [conveyor.core :refer [configure-compressor set-compressor set-input-extension add-compressor-config]])
  (:import  [com.google.javascript.jscomp.Compiler]
           [com.google.javascript.jscomp CompilerOptions CompilationLevel JSSourceFile CheckLevel]))

(defn optimization-level [options]
  (case (:compilation-level options)
    :whitespace-only CompilationLevel/WHITESPACE_ONLY
    :simple-optimizations CompilationLevel/SIMPLE_OPTIMIZATIONS
    :advanced-optimizations CompilationLevel/ADVANCED_OPTIMIZATIONS
    CompilationLevel/WHITESPACE_ONLY))

(defn compressor-for-options [options]
  (let [level (optimization-level options)]
    (fn [config body filename]
      (let [compiler (com.google.javascript.jscomp.Compiler.)
            options  (CompilerOptions.)
            extern (JSSourceFile/fromCode "externs.js" "function alert(x) {}")
            input (JSSourceFile/fromCode filename body)]
        (.setOptionsForCompilationLevel level options)
        (.compile compiler extern input options)
        (.toSource compiler)))))

(defn configure-closure
  ([config] (configure-closure config {}))
  ([config options]
    (add-compressor-config
      config
      (configure-compressor
        (set-compressor (compressor-for-options options))
        (set-input-extension "js")))))

