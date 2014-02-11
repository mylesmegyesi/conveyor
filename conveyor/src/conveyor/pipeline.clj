(ns conveyor.pipeline)

(declare ^:dynamic *pipeline*)
(declare ^:dynamic *pipeline-config*)

(defn pipeline []
  (if (bound? #'*pipeline*)
    *pipeline*
    (throw (Exception. "Pipeline config not bound."))))

(defn pipeline-config []
  (if (bound? #'*pipeline-config*)
    *pipeline-config*
    (throw (Exception. "Pipeline config not bound."))))

(def default-pipeline-config
  {:load-paths []
   :cache-dir "target/conveyor-cache"
   :strategy :runtime
   :compilers []
   :compressors []
   :prefix "/"
   :output-dir "public"
   :compress false
   :compile true
   :pipeline-enabled true})

(defn get-path [path]
  (let [pipe (pipeline)
        prefix-fn (:path-prefixer pipe)
        path-finder-fn (:path-finder pipe)]
    (if-let [path (path-finder-fn path)]
      (prefix-fn path))))
