(ns conveyor.finder.precompiled-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]))

(describe "conveyor.finder.precompiled"

  (with config (thread-pipeline-config
                 (set-asset-finder :precompiled)
                 (set-output-dir "test_fixtures/output")
                 (set-manifest "test_fixtures/output/manifest1.edn")))

  (it "finds an asset in the output directory"
    (with-pipeline-config @config
      (let [found-asset (find-asset "test1.js")]
        (should= "test1.js" (:logical-path found-asset)))))

  (it "finds an asset that has a dot in the name"
    (with-pipeline-config @config
      (let [found-asset (find-asset "jquery.ui.autocomplete.js")]
        (should= "jquery.ui.autocomplete.js" (:logical-path found-asset)))))

  (it "finds an asset in the output directory with a prefix"
    (with-pipeline-config (-> @config
                            (add-prefix "/assets")
                            (set-manifest "test_fixtures/output/manifest2.edn"))
      (let [found-asset (find-asset "test1.js")]
        (should= "/assets/test1.js" (:logical-path found-asset)))))

  (it "finds an asset in the output directory with a prefix"
    (with-pipeline-config (-> @config
                            (add-prefix "/assets")
                            (set-manifest "test_fixtures/output/manifest2.edn"))
      (let [found-asset (find-asset "test1.js")]
        (should= "/assets/test1.js" (:logical-path found-asset)))))

  )
