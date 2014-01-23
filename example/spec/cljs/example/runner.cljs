 (ns example.runner
  (:require [specljs.report.progress :refer [new-progress-reporter]]
            [specljs.report.documentation :refer [new-documentation-reporter]]
            [specljs.run.standard :as runner]))

(defn ^:export run-specs []
    (set! runner/armed true)
    (runner/run-specs :color true :reporters [(new-documentation-reporter)]))
