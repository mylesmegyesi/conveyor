(ns conveyor.core-spec
  (:require [speclj.core :refer :all]
            [clojure.java.io :refer [file]]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.precompile :refer [precompile]]
            [conveyor.file-utils :refer [slurp-or-read]]
            [ring.mock.request :as mr])
  (:import [java.io File]
           [org.apache.commons.io FileUtils]))

(describe "conveyor.core"

  (context "resource-directory-path"

    (it "builds the full path to a jar resource directory"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")]
        (should= ".test1 { color: white; }\n" (slurp-or-read (str full-path "/test1.css")))))

    (it "returns nil when the directory does not exist"
      (should-be-nil (resource-directory-path "non_existant_dir" "test1.css")))

    (it "returns nil when given resources does not exist"
      (should-be-nil (resource-directory-path "stylesheets" "unknown")))

    )

  (context "directory-path"

    (it "builds the full path to a directory"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")]
        (should= ".test2 { color: black; }\n" (slurp-or-read (str full-path "/test2.css")))))

    (it "returns nil when the directory does not exist"
      (should-be-nil (directory-path "non_existant_dir")))

    )

  (context "add resource directory to load path"

    (it "adds valid resource directory to the load path"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")
            new-config (add-validated-resource-directory {:load-paths []} "stylesheets" "test1.css")]
        (should= [full-path] (:load-paths new-config))))

    (it "throws an exception when the resource directory does not exist"
      (should-throw IllegalArgumentException "Could not find resource directory: uknown-dir"
                    (add-validated-resource-directory {:load-paths []} "uknown-dir" "test1.css")))

    )

  (context "add directory to load path"

    (it "adds valid directory to the load path"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            new-config (add-validated-directory {:load-paths []} "test_fixtures/public/stylesheets")]
        (should= [full-path] (:load-paths new-config))))

    (it "throws an exception when the directory does not exist"
      (should-throw IllegalArgumentException "Could not find directory: uknown-dir"
                    (add-validated-directory {:load-paths []} "uknown-dir")))

    )

  (context "initialize-config"

    (it "adds a resource directory path to the load path"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")
            config (initialize-config {:load-paths [{:type :resource-directory
                                                     :path "stylesheets"
                                                     :file-in-dir "test1.css"}]})]
        (should= [full-path] (:load-paths config))))

    (it "adds a directory path to the load path"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            config (initialize-config {:load-paths [{:type :directory
                                                     :path "test_fixtures/public/stylesheets"}]})]
        (should= [full-path] (:load-paths config))))

    (it "throws an exception for an unknown load path type"
      (should-throw
        Exception
        "Unknown type of load-path: :unknown-type. Valid types are :resource-directory and :directory."
        (initialize-config {:load-paths [{:type :unknown-type}]})))

    (it "configures the prefix"
      (let [config (initialize-config {:prefix "/assets"})]
        (should= "/assets" (:prefix config))))

    (it "configures a plugin"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            config (initialize-config {:plugins [:test-plugin]})]
        (should= "configured" (:test-config config))
        (should= [full-path] (:load-paths config))))

    (it "configures multiple plugins"
      (let [style-path (directory-path "test_fixtures/public/stylesheets")
            js-path (directory-path "test_fixtures/public/javascripts")
            config (initialize-config {:plugins [:test-plugin :other-plugin]})]
        (should= "configured" (:test-config config))
        (should== [style-path js-path] (:load-paths config))))

    (it "configures a plugin that takes options"
      (let [config (initialize-config {:plugins [{:plugin-name :option-plugin :option1 2}]})]
        (should= 2 (:option1 config))))

    (it "configures the asset host"
      (let [config (initialize-config {:asset-host "test-host"})]
        (should= "test-host" (:asset-host config))))

    (it "configures the use-digest-path"
      (let [config (initialize-config {:use-digest-path true})]
        (should (:use-digest-path config))))

    (it "configures the output-dir"
      (let [config (initialize-config {:output-dir "test_output"})]
        (should= "test_output" (:output-dir config))))

    (it "uses target/conveyor-cache as the default cache path"
      (let [config (initialize-config {})]
        (should= "target/conveyor-cache" (:cache-dir config))))

    (it "uses public as the default the output-dir"
      (let [config (initialize-config {:output-dir nil})]
        (should= "public" (:output-dir config))))

    (it "configures the manifest"
      (let [config (initialize-config {:manifest "some-other-manifest.edn"})]
        (should= "some-other-manifest.edn" (:manifest config))))

    (it "configures the pipeline strategy"
      (let [config (initialize-config {:strategy :runtime})]
        (should= :runtime (:strategy config))))

    (it "defaults the pipeline strategy to runtime"
      (let [config (initialize-config {})]
        (should= :runtime (:strategy config))))

    (it "sets compression to true"
      (let [config (initialize-config {:compress true})]
        (should= true (:compress config))))

    (it "sets compression to false"
      (let [config (initialize-config {:compress false})]
        (should= false (:compress config))))

    (it "defaults compression to false"
      (let [config (initialize-config {})]
        (should= false (:compress config))))

    (it "sets compile to true"
      (let [config (initialize-config {:compile true})]
        (should= true (:compile config))))

    (it "sets compile to false"
      (let [config (initialize-config {:compile false})]
        (should= false (:compile config))))

    (it "defaults compile to true"
      (let [config (initialize-config {})]
        (should= true (:compile config))))

    (it "sets pipeline-enabled to true"
      (let [config (initialize-config {:pipeline-enabled true})]
        (should= true (:pipeline-enabled config))))

    (it "sets pipeline-enabled to true"
      (let [config (initialize-config {:pipeline-enabled false})]
        (should= false (:pipeline-enabled config))))

    (it "defaults pipeline-enabled to true"
      (let [config (initialize-config {})]
        (should= true (:pipeline-enabled config))))
    )

  (context "find-asset"

    (defn- it-finds-assets [strategy prepare-asset]
      (list

        (def alphanumeric "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890")
        (defn get-output-dir [length]
          (loop [acc []]
            (if (= (count acc) length) (apply str acc)
              (recur (conj acc (rand-nth alphanumeric))))))

        (around [it]
          (let [config (thread-pipeline-config
                         (set-output-dir (get-output-dir 15)) ; the manifest file get's cached once it is read, so write to a different output directory each test
                         (set-strategy strategy)
                         (add-directory-to-load-path "test_fixtures/public/javascripts"))]
            (with-pipeline-config config
              (try
                (it)
                (finally
                  (FileUtils/deleteDirectory (file (:output-dir config))))))))

        (it "finds an asset and returns the body"
          (prepare-asset "test1.js")
          (let [found-asset (find-asset "test1.js")]
            (should= "var test = 1;\n" (slurp-or-read (:body found-asset)))))

        (it "returns a static file as a file"
          (prepare-asset "test1.js")
          (let [found-asset (find-asset "test1.js")]
            (should= File (.getClass (:body found-asset)))))

        (it "returns the logical path"
          (prepare-asset "test1.js")
          (let [asset (find-asset "test1.js")]
            (should= "test1.js" (:logical-path asset))
            (should= "/test1.js" (asset-url "test1.js"))
            (should= (slurp-or-read (:body asset)) (slurp-or-read (:body (find-asset (:logical-path asset)))))))

        (it "does not return the digest and digest path"
          (prepare-asset "test1.js")
          (let [asset (find-asset "test1.js")]
            (should-be-nil (:digest asset))
            (should-be-nil (:digest-path asset))))

        (it "returns the digest and digest path if configured to"
          (with-pipeline-config (set-use-digest-path (pipeline-config) true)
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "200368af90cc4c6f4f1ddf36f97a279e" (:digest asset))
              (should= "test1-200368af90cc4c6f4f1ddf36f97a279e.js" (:digest-path asset)))))

        (it "maintains an iefix suffix"
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js?#iefix")]
              (should= "test1.js?#iefix" (:logical-path asset))))

        (it "finds an asset with multiple load paths"
          (with-pipeline-config (add-directory-to-load-path (pipeline-config) "test_fixtures/public/stylesheets")
            (prepare-asset "test2.css")
            (let [asset (find-asset "test2.css")]
              (should= ".test2 { color: black; }\n" (slurp-or-read (:body asset)))
              (should= "test2.css" (:logical-path asset))
              (should= "/test2.css" (asset-url "test2.css")))))

        (it "finds an asset with a resource directory on its load path"
          (with-pipeline-config (add-resource-directory-to-load-path (pipeline-config) "stylesheets" "test1.css")
            (prepare-asset "test1.css")
            (let [asset (find-asset "test1.css")]
              (should= ".test1 { color: white; }\n" (slurp-or-read (:body asset)))
              (should= "test1.css" (:logical-path asset))
              (should= "/test1.css" (asset-url "test1.css")))))

        (it "returns nil if the asset could not be found"
          (prepare-asset "test1.js")
          (should-be-nil (find-asset "non-existant-file")))

        (it "throws if the asset could not be found"
          (prepare-asset "test1.js")
          (should-throw
            Exception
            "Asset not found: non-existant-file"
            (find-asset! "non-existant-file")))

        (with fake-compiler-config (add-compiler-config
                                     (pipeline-config)
                                     (configure-compiler
                                       (add-input-extension "fake")
                                       (add-output-extension "fake-output"))))


        (it "finds assets using the output extensions given by compilers"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test2.fake-output")
            (let [asset (find-asset "test2.fake-output")]
              (should= "Some fake thing\n" (slurp-or-read (:body asset)))
              (should= "test2.fake-output" (:logical-path asset))
              (should= "/test2.fake-output" (asset-url "test2.fake-output")))))

        (it "maintains file extension suffix"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test2.fake-output")
            (let [asset (find-asset "test2.fake-output?#wat")]
              (should= "Some fake thing\n" (slurp-or-read (:body asset)))
              (should= "test2.fake-output?#wat" (:logical-path asset))
              (should= "/test2.fake-output?#wat" (asset-url "test2.fake-output?#wat")))))

        (it "finds assets using the output extensions given by compilers if the file name has many dots"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test.2.fake-output")
            (let [asset (find-asset "test.2.fake-output")]
              (should= "Some fake thing with dots\n" (slurp-or-read (:body asset)))
              (should= "test.2.fake-output" (:logical-path asset))
              (should= "/test.2.fake-output" (asset-url "test.2.fake-output")))))

        (it "finds assets using the input extensions given by compilers if the file name has many dots"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test.2.fake")
            (let [asset (find-asset "test.2.fake")]
              (should= "Some fake thing with dots\n" (slurp-or-read (:body asset)))
              (should= "test.2.fake" (:logical-path asset))
              (should= "/test.2.fake" (asset-url "test.2.fake")))))

        (it "returns nil if the file is found, but the requested file extension does not match any compilers output extensions"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test2.fake-output")
            (should-be-nil (find-asset "test2.bad-ext"))))

        (with fake1-compiler-config (add-compiler-config
                                      (pipeline-config)
                                      (configure-compiler
                                        (add-input-extension "fake")
                                        (add-input-extension "fake1")
                                        (add-output-extension "fake-output"))))

        (it "finds an asset using any of the configured compilers extensions"
          (with-pipeline-config @fake1-compiler-config
            (prepare-asset "test3.fake-output")
            (let [asset (find-asset "test3.fake-output")]
              (should= "Some fake thing1\n" (slurp-or-read (:body asset)))
              (should= "test3.fake-output" (:logical-path asset))
              (should= "/test3.fake-output" (asset-url "test3.fake-output")))))

        (defn test-compiler [config asset input-extension output-extension]
          (let [body (str (:body asset) "compiled with " (:absolute-path asset) ":" input-extension ":" output-extension)]
            (assoc asset :body body)))

        (with test-compiler-config (add-compiler-config
                                     (pipeline-config)
                                     (configure-compiler
                                       (set-compiler test-compiler)
                                       (add-input-extension "fake1")
                                       (add-output-extension "fake-output"))))

        (it "compiles the asset"
          (with-pipeline-config @test-compiler-config
            (prepare-asset "test3.fake-output")
            (let [base-path (directory-path "test_fixtures/public/javascripts")
                  asset (find-asset "test3.fake-output")]
              (should= (format "Some fake thing1\ncompiled with %s:fake1:fake-output" (str base-path "/test3.fake1")) (slurp-or-read (:body asset)))
              (should= "test3.fake-output" (:logical-path asset))
              (should= "/test3.fake-output" (asset-url "test3.fake-output")))))

        (it "does not compile a static file"
          (with-pipeline-config @test-compiler-config
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\n" (slurp-or-read (:body asset)))
              (should= "test1.js" (:logical-path asset))
              (should= "/test1.js" (asset-url "test1.js")))))

        (defn test-compressor [config body filename]
          (str body "compressed"))

        (with test-compressor-config (-> (pipeline-config)
                                       (set-compression true)
                                       (add-compressor-config
                                         (configure-compressor
                                           (set-compressor test-compressor)
                                           (set-input-extension "js")))))

        (it "compresses the asset"
          (with-pipeline-config @test-compressor-config
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\ncompressed" (slurp-or-read (:body asset))))))

        (it "only uses the compresses that matches the extension"
          (with-pipeline-config @test-compressor-config
            (prepare-asset "test2.fake")
            (let [asset (find-asset "test2.fake")]
              (should= "Some fake thing\n" (slurp-or-read (:body asset))))))

        (it "does not compress the asset when disabled"
          (with-pipeline-config (set-compression @test-compressor-config false)
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\n" (slurp-or-read (:body asset))))))

        (it "does not compress the asset when the pipeline is disabled"
          (with-pipeline-config (set-pipeline-enabled @test-compressor-config false)
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\n" (slurp-or-read (:body asset))))))

        (it "a compiler with multiple extensions"
          (with-pipeline-config (add-compiler-config
                                  (pipeline-config)
                                  (configure-compiler
                                    (add-input-extension "fake")
                                    (add-input-extension "fake2")
                                    (add-output-extension "fake-output")))
              (prepare-asset "test1.js")
              (should-be-nil (find-asset "test2.bad-ext"))))

        (with markdown-config (add-compiler-config
                                (pipeline-config)
                                (configure-compiler
                                  (add-input-extension "markdown")
                                  (add-output-extension "html")
                                  (add-output-extension "txt"))))

        (it "a compiler with multiple output extensions"
          (with-pipeline-config @markdown-config
            (prepare-asset ["multiple_outputs.html" "multiple_outputs.txt"])
            (let [html-asset (find-asset "multiple_outputs.html")
                  txt-asset (find-asset "multiple_outputs.txt")]
              (should= "Multiple outputs\n" (slurp-or-read (:body html-asset)))
              (should= "multiple_outputs.html" (:logical-path html-asset))
              (should= "/multiple_outputs.html" (asset-url "multiple_outputs.html"))
              (should= "Multiple outputs\n" (slurp-or-read (:body txt-asset)))
              (should= "multiple_outputs.txt" (:logical-path txt-asset))
              (should= "/multiple_outputs.txt" (asset-url "multiple_outputs.txt")))))

        (with markdown-html-config (configure-compiler
                                     (add-input-extension "markdown")
                                     (add-output-extension "html")))

        (with markdown-txt-config (configure-compiler
                                    (add-input-extension "markdown")
                                    (add-output-extension "txt")))

        (it "multiple compilers match on input type but only one matches on output type"
          (with-pipeline-config (-> (pipeline-config)
                                  (add-compiler-config @markdown-html-config)
                                  (add-compiler-config @markdown-txt-config))
            (prepare-asset ["multiple_outputs.html" "multiple_outputs.txt"])
            (let [html-asset (find-asset "multiple_outputs.html")
                  txt-asset (find-asset "multiple_outputs.txt")]
            (should= "Multiple outputs\n" (slurp-or-read (:body html-asset)))
            (should= "multiple_outputs.html" (:logical-path html-asset))
            (should= "/multiple_outputs.html" (asset-url "multiple_outputs.html"))
            (should= "Multiple outputs\n" (slurp-or-read (:body txt-asset)))
            (should= "multiple_outputs.txt" (:logical-path txt-asset))
            (should= "/multiple_outputs.txt" (asset-url "multiple_outputs.txt")))))

        (it "returns nil if the digest does not match"
          (with-pipeline-config @markdown-config
            (prepare-asset "test1.js")
            (should-be-nil (find-asset "test1-200368af90cc4c6f4f1ddf36f97a2bad.js"))))

        (with coffeescript-config (add-compiler-config
                                    (pipeline-config)
                                    (configure-compiler
                                      (add-input-extension "coffee")
                                      (add-output-extension "js"))))

        ))

    (context "using the runtime pipeline"
      (it-finds-assets :runtime (fn [_] )))

    (context "using the precompiled pipeline"
      (it-finds-assets :precompiled #(with-pipeline-config
                                       (set-strategy (pipeline-config) :runtime)
                                       (precompile (flatten [%])))))
    )

  (context "asset-url"

    (defn- it-finds-the-asset-url [strategy prepare-asset]

      (list
        (with config (thread-pipeline-config
                       (set-strategy strategy)
                       (set-output-dir "public")
                       (add-directory-to-load-path "test_fixtures/public/javascripts")))
        (around [it]
          (try
            (it)
            (finally
              (FileUtils/deleteDirectory (file (:output-dir @config))))))

        (context "with no asset-host"
          (around [it]
            (with-pipeline-config @config
              (it)))

          (it "returns the logical path"
            (prepare-asset "test1.js")
            (should= "/test1.js" (asset-url "test1.js")))

          (it "throws if the file does not exist"
            (prepare-asset "test1.js")
            (should-throw
              Exception
              "Asset not found: unknown.js"
              (asset-url "unknown.js")))

          (with coffeescript-config (add-compiler-config
                                      (pipeline-config)
                                      (configure-compiler
                                        (add-input-extension "coffee")
                                        (add-output-extension "js"))))

          (it "returns the logical path of an asset with dots in the name"
            (with-pipeline-config @coffeescript-config
              (prepare-asset "test.6.js")
              (should= "/test.6.js" (asset-url "test.6.js"))))

          (it "appends the prefix"
            (with-pipeline-config (add-prefix (pipeline-config) "/assets")
              (prepare-asset "test1.js")
              (should= "/assets/test1.js" (asset-url "test1.js"))))

          (it "appends a leading '/' to the prefix if it doesn't have one"
            (with-pipeline-config (add-prefix (pipeline-config) "/assets")
              (prepare-asset "test1.js")
              (should= "/assets/test1.js" (asset-url "test1.js"))))

          (it "uses the digest path if configured to"
            (with-pipeline-config (set-use-digest-path (pipeline-config) true)
              (prepare-asset "test1.js")
              (should= "/test1-200368af90cc4c6f4f1ddf36f97a279e.js" (asset-url "test1.js"))))

          )

        (context "with an asset-host"

          (around [it]
            (with-pipeline-config
              (set-asset-host @config "http://cloudfront.net")
              (it)))

          (it "returns the logical path"
            (prepare-asset "test1.js")
            (should= "http://cloudfront.net/test1.js" (asset-url "test1.js")))

          (it "returns nil if the file does not exist"
            (should-throw
              Exception
              "Asset not found: unknown.js"
              (asset-url "unknown.js")))

          (it "returns the path if the host is nil"
            (with-pipeline-config (set-asset-host (pipeline-config) nil)
              (prepare-asset "test1.js")
              (should= "/test1.js" (asset-url "test1.js"))))

          (it "appends the prefix"
            (with-pipeline-config (add-prefix (pipeline-config) "/assets")
              (prepare-asset "test1.js")
              (should= "http://cloudfront.net/assets/test1.js" (asset-url "test1.js"))))

          (it "builds the url correctly when the asset host contains a trailing slash"
            (with-pipeline-config (set-asset-host (pipeline-config) "http://cloudfront.net/")
              (prepare-asset "test1.js")
              (should= "http://cloudfront.net/test1.js" (asset-url "test1.js"))))

          (it "uses the digest path if configured to"
            (with-pipeline-config (set-use-digest-path (pipeline-config) true)
              (prepare-asset "test1.js")
              (should= "http://cloudfront.net/test1-200368af90cc4c6f4f1ddf36f97a279e.js" (asset-url "test1.js"))))

          )
        )
      )

    (context "using the runtime pipeline"
      (it-finds-the-asset-url :runtime (fn [_] )))

    (context "using the precompiled pipeline"
      (it-finds-the-asset-url :precompiled #(with-pipeline-config
                                              (set-strategy (pipeline-config) :runtime)
                                              (precompile (flatten [%])))))

    )

  )
