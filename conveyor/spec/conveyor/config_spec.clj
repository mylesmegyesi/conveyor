(ns conveyor.config-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]))

(describe "conveyor.config"

  (describe "thread pipeline config"

    (it "threads add-compiler-config"
      (let [config (thread-pipeline-config
                     (add-compiler-config :my-compiler))]
        (should= {:compilers [:my-compiler]} config)))

    (it "threads add-compressor-config"
      (let [config (thread-pipeline-config
                     (add-compressor-config :my-compressor))]
        (should= {:compressors [:my-compressor]} config)))

    (it "threads add-output-extension"
      (let [config (thread-pipeline-config
                     (add-output-extension "my-extension"))]
        (should= {:output-extensions ["my-extension"]} config)))

    (it "threads add-input-extension"
      (let [config (thread-pipeline-config
                     (add-input-extension "my-extension"))]
        (should= {:input-extensions ["my-extension"]} config)))

    (it "threads set-compiler"
      (let [config (thread-pipeline-config
                     (set-compiler :my-compiler))]
        (should= {:compiler :my-compiler} config)))

    (it "threads set-compressor"
      (let [config (thread-pipeline-config
                     (set-compressor :my-compressor))]
        (should= {:compressor :my-compressor} config)))

    (it "threads add-prefix"
      (let [config (thread-pipeline-config
                     (add-prefix "/prefix"))]
        (should= {:prefix "/prefix"} config)))

    (it "threads set-use-digest-path"
      (let [config (thread-pipeline-config
                     (set-use-digest-path true))]
        (should= {:use-digest-path true} config)))

    (it "threads set-output-dir"
      (let [config (thread-pipeline-config
                     (set-output-dir "output"))]
        (should= {:output-dir "output"} config)))

    (it "threads set-asset-finder"
      (let [config (thread-pipeline-config
                     (set-asset-finder :my-finder))]
        (should= {:asset-finder :my-finder} config)))

    (it "threads set-asset-host"
      (let [config (thread-pipeline-config
                     (set-asset-host "my-host/"))]
        (should= {:asset-host "my-host"} config)))

    (it "threads set-manifest"
      (let [config (thread-pipeline-config
                     (set-manifest "manifest"))]
        (should= {:manifest "manifest"} config)))

    (it "threads set-compression"
      (let [config (thread-pipeline-config
                     (set-compression true))]
        (should= {:compress true} config)))

    (it "threads set-compile"
      (let [config (thread-pipeline-config
                     (set-compile true))]
        (should= {:compile true} config)))

    (it "threads set-pipeline-enabled"
      (let [config (thread-pipeline-config
                     (set-pipeline-enabled true))]
        (should= {:pipeline-enabled true} config)))

    (it "threads multiple setters"
      (let [config (thread-pipeline-config
                     (set-asset-finder :my-finder)
                     (set-compile false)
                     (add-compressor-config :my-config))]
        (should= {:asset-finder :my-finder
                  :compile false
                  :compressors [:my-config]} config)))

  )

  (describe "add-directory-to-load-path"
    (it "adds the path-map to the load-paths key"
      (let [config (add-directory-to-load-path {} "/test")]
        (should= {:load-paths [{:type :directory
                                 :path "/test"}]}
                config)))
  )

  (describe "add-resource-directoy-to-load-path"
    (it "adds the path-map to the load-paths key"
      (let [config (add-resource-directory-to-load-path {:compilers []} "/test" "test.txt")]
        (should= {:load-paths [{:type :resource-directory
                                :path "/test"
                                :file-in-dir "test.txt"}]
                  :compilers []}
                 config)))
    )
)
