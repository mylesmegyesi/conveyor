(ns conveyor.core-spec
  (:require [speclj.core :refer :all]
            [clojure.java.io :refer [file]]
            [ring.mock.request :as mr]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]
            [conveyor.precompile :refer [precompile]])
  (:import [org.apache.commons.io FileUtils]))

(describe "conveyor.core"

  (context "find-asset"

    (defn- it-finds-assets [search-strategy prepare-asset]
      (list

        (def alphanumeric "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890")
        (defn get-output-dir [length]
          (loop [acc []]
            (if (= (count acc) length) (apply str acc)
              (recur (conj acc (rand-nth alphanumeric))))))

        (around [it]
          (let [config (thread-pipeline-config
                         (set-output-dir (get-output-dir 15)) ; the manifest file get's cached once it is read, so write to a different output directory each test
                         (set-search-strategy search-strategy)
                         (add-directory-to-load-path "test_fixtures/public/javascripts"))]
            (with-pipeline-config config
              (try
                (it)
                (finally
                  (FileUtils/deleteDirectory (file (:output-dir config))))))))

        (it "finds an asset and returns the body"
          (prepare-asset "test1.js")
          (let [found-asset (find-asset "test1.js")]
            (should= "var test = 1;\n" (:body found-asset))))

        (it "finds an asset and returns the body with a requested output extension"
          (prepare-asset "test1.js")
          (let [found-asset (find-asset "test1" "js")]
            (should= "var test = 1;\n" (:body found-asset))))

        (it "returns the logical path"
          (prepare-asset "test1.js")
          (let [asset (find-asset "test1.js")]
            (should= "test1.js" (:logical-path asset))
            (should= "/test1.js" (asset-url "test1.js"))
            (should= asset (find-asset (:logical-path asset)))))

        (it "returns the digest and digest path"
          (prepare-asset "test1.js")
          (let [asset (find-asset "test1.js")]
            (should= "200368af90cc4c6f4f1ddf36f97a279e" (:digest asset))
            (should= "test1-200368af90cc4c6f4f1ddf36f97a279e.js" (:digest-path asset))))

        (it "finds an asset with multiple load paths"
          (with-pipeline-config (add-directory-to-load-path (pipeline-config) "test_fixtures/public/stylesheets")
            (prepare-asset "test2.css")
            (let [asset (find-asset "test2.css")]
              (should= ".test2 { color: black; }\n" (:body asset))
              (should= "test2.css" (:logical-path asset))
              (should= "/test2.css" (asset-url "test2.css"))
              (should= "test2-9d7e7252425acc78ff419cf3d37a7820.css" (:digest-path asset)))))

        (it "finds an asset with a resource directory on its load path"
          (with-pipeline-config (add-resource-directory-to-load-path (pipeline-config) "stylesheets" "test1.css")
            (prepare-asset "test1.css")
            (let [asset (find-asset "test1.css")]
              (should= ".test1 { color: white; }\n" (:body asset))
              (should= "test1.css" (:logical-path asset))
              (should= "/test1.css" (asset-url "test1.css"))
              (should= "test1-89df887049f959cbe331b1da471f7e24.css" (:digest-path asset)))))

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
              (should= "Some fake thing\n" (:body asset))
              (should= "test2.fake-output" (:logical-path asset))
              (should= "/test2.fake-output" (asset-url "test2.fake-output"))
              (should= "test2-979d812cfd0a7dc744af9e083a63ff10.fake-output" (:digest-path asset)))))

        (it "finds assets using the output extensions given by compilers if the file name has many dots"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test.2.fake-output")
            (let [asset (find-asset "test.2" "fake-output")]
              (should= "Some fake thing with dots\n" (:body asset))
              (should= "test.2.fake-output" (:logical-path asset))
              (should= "/test.2.fake-output" (asset-url "test.2" "fake-output"))
              (should= "/test.2.fake-output" (asset-url "test.2.fake-output"))
              (should= "test.2-e2cb442c231d4d2420a64a834c86324c.fake-output" (:digest-path asset)))))

        (it "finds assets using the input extensions given by compilers if the file name has many dots"
          (with-pipeline-config @fake-compiler-config
            (prepare-asset "test.2.fake")
            (let [asset (find-asset "test.2" "fake")]
              (should= "Some fake thing with dots\n" (:body asset))
              (should= "test.2.fake" (:logical-path asset))
              (should= "/test.2.fake" (asset-url "test.2" "fake"))
              (should= "/test.2.fake" (asset-url "test.2.fake"))
              (should= "test.2-e2cb442c231d4d2420a64a834c86324c.fake" (:digest-path asset)))))

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
              (should= "Some fake thing1\n" (:body asset))
              (should= "test3.fake-output" (:logical-path asset))
              (should= "/test3.fake-output" (asset-url "test3.fake-output"))
              (should= "/test3.fake-output" (asset-url "test3" "fake-output")))))

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
              (should= (format "Some fake thing1\ncompiled with %s:fake1:fake-output" (str base-path "/test3.fake1")) (:body asset))
              (should= "test3.fake-output" (:logical-path asset))
              (should= "/test3.fake-output" (asset-url "test3.fake-output"))
              (should= "/test3.fake-output" (asset-url "test3" "fake-output")))))

        (it "does not find assets using compiler extensions when compile is disabled"
          (with-pipeline-config (set-compile @test-compiler-config false)
            (prepare-asset "test3.fake1")
            (let [asset (find-asset "test3.fake1")]
              (should-be-nil (find-asset "test3.fake-output"))
              (should= "Some fake thing1\n" (:body asset))
              (should= "test3.fake1" (:logical-path asset))
              (should= "/test3.fake1" (asset-url "test3.fake1"))
              (should= "/test3.fake1" (asset-url "test3" "fake1")))))

        (it "does not find assets using compiler extensions when the pipeline is disabled"
          (with-pipeline-config (set-pipeline-enabled @test-compiler-config false)
            (prepare-asset "test3.fake1")
            (let [asset (find-asset "test3.fake1")]
              (prepare-asset "test1.js")
              (should-be-nil (find-asset "test3.fake-output"))
              (should= "Some fake thing1\n" (:body asset))
              (should= "test3.fake1" (:logical-path asset))
              (should= "/test3.fake1" (asset-url "test3.fake1"))
              (should= "/test3.fake1" (asset-url "test3" "fake1")))))

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
              (should= "var test = 1;\ncompressed" (:body asset)))))

        (it "only uses the compresses that matches the extension"
          (with-pipeline-config @test-compressor-config
            (prepare-asset "test2.fake")
            (let [asset (find-asset "test2.fake")]
              (should= "Some fake thing\n" (:body asset)))))

        (it "does not compress the asset when disabled"
          (with-pipeline-config (set-compression @test-compressor-config false)
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\n" (:body asset)))))

        (it "does not compress the asset when the pipeline is disabled"
          (with-pipeline-config (set-pipeline-enabled @test-compressor-config false)
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= "var test = 1;\n" (:body asset)))))

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
              (should= "Multiple outputs\n" (:body html-asset))
              (should= "multiple_outputs.html" (:logical-path html-asset))
              (should= "/multiple_outputs.html" (asset-url "multiple_outputs.html"))
              (should= "/multiple_outputs.html" (asset-url "multiple_outputs" "html"))
              (should= "Multiple outputs\n" (:body txt-asset))
              (should= "multiple_outputs.txt" (:logical-path txt-asset))
              (should= "/multiple_outputs.txt" (asset-url "multiple_outputs.txt"))
              (should= "/multiple_outputs.txt" (asset-url "multiple_outputs" "txt")))))

        (with markdown-html-config (configure-compiler
                                     (add-input-extension "markdown")
                                     (add-output-extension "html")))

        (with markdown-txt-config (configure-compiler
                                    (add-input-extension "markdown")
                                    (add-output-extension "txt")))

        (it "mutliple compilers match on input type but only one matches on output type"
          (with-pipeline-config (-> (pipeline-config)
                                  (add-compiler-config @markdown-html-config)
                                  (add-compiler-config @markdown-txt-config))
            (prepare-asset ["multiple_outputs.html" "multiple_outputs.txt"])
            (let [html-asset (find-asset "multiple_outputs.html")
                  txt-asset (find-asset "multiple_outputs.txt")]
            (should= "Multiple outputs\n" (:body html-asset))
            (should= "multiple_outputs.html" (:logical-path html-asset))
            (should= "/multiple_outputs.html" (asset-url "multiple_outputs.html"))
            (should= "/multiple_outputs.html" (asset-url "multiple_outputs" "html"))
            (should= "Multiple outputs\n" (:body txt-asset))
            (should= "multiple_outputs.txt" (:logical-path txt-asset))
            (should= "/multiple_outputs.txt" (asset-url "multiple_outputs.txt"))
            (should= "/multiple_outputs.txt" (asset-url "multiple_outputs" "txt")))))

        (it "finds an asset using the digest path"
          (with-pipeline-config @markdown-config
            (prepare-asset "test1.js")
            (let [asset (find-asset "test1.js")]
              (should= asset (find-asset (:digest-path asset))))))

        (it "returns nil if the digest does not match"
          (with-pipeline-config @markdown-config
            (prepare-asset "test1.js")
            (should-be-nil (find-asset "test1-200368af90cc4c6f4f1ddf36f97a2bad.js"))))

        (with coffeescript-config (add-compiler-config
                                    (pipeline-config)
                                    (configure-compiler
                                      (add-input-extension "coffee")
                                      (add-output-extension "js"))))

        (it "finds an index file"
          (with-pipeline-config @coffeescript-config
            (prepare-asset "test6.js")
            (let [asset (find-asset "test6.js")]
              (should= "var index = 1;\n" (:body asset))
              (should= "test6.js" (:logical-path asset))
              (should= "/test6.js" (asset-url "test6.js"))
              (should= "/test6.js" (asset-url "test6" "js")))))

        (it "finds an index file with dots in the directory name"
          (with-pipeline-config @coffeescript-config
            (prepare-asset "test.6.js")
            (let [asset (find-asset "test.6.js")]
              (should= "var index6 = 1;\n" (:body asset))
              (should= "test.6.js" (:logical-path asset))
              (should= "/test.6.js" (asset-url "test.6" "js"))
              (should= "/test.6.js" (asset-url "test.6.js")))))

        (it "finds an index file with a matching output extension"
          (with-pipeline-config @coffeescript-config
            (prepare-asset "test7.js")
            (let [asset (find-asset "test7.js")]
              (should= "var test7 = 1;\n" (:body asset))
              (should= "test7.js" (:logical-path asset))
              (should= "/test7.js" (asset-url "test7.js"))
              (should= "/test7.js" (asset-url "test7" "js")))))

        ))

    (context "on the load path"
      (it-finds-assets :dynamic (fn [_] )))

    (context "in the output path"
      (it-finds-assets :static #(with-pipeline-config
                                  (set-search-strategy (pipeline-config) :dynamic)
                                  (precompile (flatten [%])))))
    )

  (context "asset-url"

    (defn- it-finds-the-asset-url [search-strategy prepare-asset]

      (list
        (with config (thread-pipeline-config
                       (set-search-strategy search-strategy)
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

          (it "returns the logical path with an given output extension"
            (prepare-asset "test1.js")
            (should= "/test1.js" (asset-url "test1" "js")))

          (with coffeescript-config (add-compiler-config
                                      (pipeline-config)
                                      (configure-compiler
                                        (add-input-extension "coffee")
                                        (add-output-extension "js"))))

          (it "returns the logical path of an asset with dots in the name"
            (with-pipeline-config @coffeescript-config
              (prepare-asset "test.6.js")
              (should= "/test.6.js" (asset-url "test.6" "js"))))

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

          (it "returns the logical path with an given output extension"
            (prepare-asset "test1.js")
            (should= "http://cloudfront.net/test1.js" (asset-url "test1" "js")))

          (it "appends the prefix"
            (with-pipeline-config (add-prefix (pipeline-config) "/assets")
              (prepare-asset "test1.js")
              (should= "http://cloudfront.net/assets/test1.js" (asset-url "test1.js"))))

          (it "removes trailing '/' from host"
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

    (context "on the load path"
      (it-finds-the-asset-url :dynamic (fn [_] )))

    (context "in the output path"
      (it-finds-the-asset-url :static #(with-pipeline-config
                                         (set-search-strategy (pipeline-config) :dynamic)
                                         (precompile (flatten [%])))))

    )

  )
