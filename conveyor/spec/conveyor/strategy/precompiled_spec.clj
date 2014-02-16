(ns conveyor.strategy.precompiled-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.asset-body :refer [body-to-string]]
            [conveyor.config :refer :all])
  (:import [java.io File]))

(describe "conveyor.pipeline.precompiled"

  (with config (thread-pipeline-config
                 (set-strategy :precompiled)
                 (set-output-dir "test_fixtures/output")
                 (set-manifest "test_fixtures/output/manifest1.edn")))

  (it "finds an asset in the output directory"
    (with-pipeline-config @config
      (let [found-asset (find-asset "test1.js")]
        (should= "test1.js" (:logical-path found-asset)))))

  (it "returns the body as a file"
    (with-pipeline-config @config
      (let [found-asset (find-asset "test1.js")]
        (should= File (type (:body found-asset))))))

  (it "finds an asset that has a dot in the name"
    (with-pipeline-config @config
      (let [found-asset (find-asset "jquery.ui.autocomplete.js")]
        (should= "jquery.ui.autocomplete.js" (:logical-path found-asset)))))

  (it "finds an asset in the output directory with a prefix"
    (with-pipeline-config (-> @config
                            (add-prefix "/assets")
                            (set-manifest "test_fixtures/output/manifest2.edn"))
      (let [found-asset (find-asset "test1.js")]
        (should= "/test1.js" (:logical-path found-asset)))))

  (it "finds an asset in the output directory with a prefix"
    (with-pipeline-config (-> @config
                            (add-prefix "/assets")
                            (set-manifest "test_fixtures/output/manifest2.edn"))
      (let [found-asset (find-asset "test1.js")]
        (should= "/test1.js" (:logical-path found-asset))
        (should (body-to-string (:body found-asset))))))

  )
