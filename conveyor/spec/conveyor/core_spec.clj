(ns conveyor.core-spec
  (:require [speclj.core :refer :all]
            [clojure.java.io :refer [file input-stream copy]]
            [clojure.edn :refer [read-string] :rename {read-string read-edn}]
            [ring.mock.request :as mr]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]
            [conveyor.file-utils :refer [read-file read-stream]])
  (:import [org.apache.commons.io FileUtils]
           [java.io ByteArrayOutputStream FileInputStream]
           [java.util.zip GZIPInputStream]))

(describe "conveyor.core"

  (context "find-asset"

    (defn- it-finds-assets [search-strategy prepare-asset]
      (list

        (def alphanumeric "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890")
        (defn get-output-dir [length]
          (loop [acc []]
            (if (= (count acc) length) (apply str acc)
              (recur (conj acc (rand-nth alphanumeric))))))

        (with config (thread-pipeline-config
                       (set-output-dir (get-output-dir 15)) ; the manifest file get's cached once it is read, so write to a different output directory each test
                       (set-search-strategy search-strategy)
                       (add-directory-to-load-path "test_fixtures/public/javascripts")))

        (after
          (FileUtils/deleteDirectory (file (:output-dir @config))))

        (it "finds an asset and returns the body"
          (prepare-asset @config "test1.js")
          (let [found-asset (find-asset @config "test1.js")]
            (should= "var test = 1;\n" (:body found-asset))))

        (it "finds an asset and returns the body with a requested output extension"
          (prepare-asset @config "test1.js")
          (let [found-asset (find-asset @config "test1" "js")]
            (should= "var test = 1;\n" (:body found-asset))))

        (it "returns the logical path"
          (prepare-asset @config "test1.js")
          (let [asset (find-asset @config "test1.js")]
            (should= "test1.js" (:logical-path asset))
            (should= "/test1.js" (asset-path @config "test1.js"))
            (should= asset (find-asset @config (:logical-path asset)))))

        (it "returns the digest and digest path"
          (prepare-asset @config "test1.js")
          (let [asset (find-asset @config "test1.js")]
            (should= "200368af90cc4c6f4f1ddf36f97a279e" (:digest asset))
            (should= "test1-200368af90cc4c6f4f1ddf36f97a279e.js" (:digest-path asset))))

        (it "finds an asset with multiple load paths"
          (let [config (add-directory-to-load-path @config "test_fixtures/public/stylesheets")
                _ (prepare-asset config "test2.css")
                asset (find-asset config "test2.css")]
            (should= ".test2 { color: black; }\n" (:body asset))
            (should= "test2.css" (:logical-path asset))
            (should= "/test2.css" (asset-path config "test2.css"))
            (should= "test2-9d7e7252425acc78ff419cf3d37a7820.css" (:digest-path asset))))

        (it "finds an asset with a resource directory on its load path"
          (let [config (add-resource-directory-to-load-path @config "stylesheets" "test1.css")
                _ (prepare-asset config "test1.css")
                asset (find-asset config "test1.css")]
            (should= ".test1 { color: white; }\n" (:body asset))
            (should= "test1.css" (:logical-path asset))
            (should= "/test1.css" (asset-path config "test1.css"))
            (should= "test1-89df887049f959cbe331b1da471f7e24.css" (:digest-path asset))))

        (it "returns nil if the asset could not be found"
          (prepare-asset @config "test1.js")
          (should-be-nil (find-asset @config "non-existant-file")))

        (it "! throws if the asset could not be found"
          (prepare-asset @config "test1.js")
          (should-throw
            Exception
            "Asset not found: non-existant-file"
            (find-asset! @config "non-existant-file")))

        (with fake-compiler-config (add-compiler-config
                                     @config
                                     (configure-compiler
                                       (add-input-extension "fake")
                                       (add-output-extension "fake-output"))))

        (it "finds assets using the output extensions given by compilers"
          (prepare-asset @fake-compiler-config "test2.fake-output")
          (let [asset (find-asset @fake-compiler-config "test2.fake-output")]
            (should= "Some fake thing\n" (:body asset))
            (should= "test2.fake-output" (:logical-path asset))
            (should= "/test2.fake-output" (asset-path @fake-compiler-config "test2.fake-output"))
            (should= "test2-979d812cfd0a7dc744af9e083a63ff10.fake-output" (:digest-path asset))))

        (it "finds assets using the output extensions given by compilers if the file name has many dots"
          (prepare-asset @fake-compiler-config "test.2.fake-output")
          (let [asset (find-asset @fake-compiler-config "test.2" "fake-output")]
            (should= "Some fake thing with dots\n" (:body asset))
            (should= "test.2.fake-output" (:logical-path asset))
            (should= "/test.2.fake-output" (asset-path @fake-compiler-config "test.2" "fake-output"))
            (should= "/test.2.fake-output" (asset-path @fake-compiler-config "test.2.fake-output"))
            (should= "test.2-e2cb442c231d4d2420a64a834c86324c.fake-output" (:digest-path asset))))

        (it "finds assets using the input extensions given by compilers if the file name has many dots"
          (prepare-asset @fake-compiler-config "test.2.fake")
          (let [asset (find-asset @fake-compiler-config "test.2" "fake")]
            (should= "Some fake thing with dots\n" (:body asset))
            (should= "test.2.fake" (:logical-path asset))
            (should= "/test.2.fake" (asset-path @fake-compiler-config "test.2" "fake"))
            (should= "/test.2.fake" (asset-path @fake-compiler-config "test.2.fake"))
            (should= "test.2-e2cb442c231d4d2420a64a834c86324c.fake" (:digest-path asset))))

        (it "returns nil if the file is found, but the requested file extension does not match any compilers output extensions"
          (prepare-asset @fake-compiler-config "test2.fake-output")
          (should-be-nil (find-asset @fake-compiler-config "test2.bad-ext")))

        (with fake1-compiler-config (add-compiler-config
                                      @config
                                      (configure-compiler
                                        (add-input-extension "fake")
                                        (add-input-extension "fake1")
                                        (add-output-extension "fake-output"))))

        (it "finds an asset using any of the configured compilers extensions"
          (prepare-asset @fake1-compiler-config "test3.fake-output")
          (let [asset (find-asset @fake1-compiler-config "test3.fake-output")]
            (should= "Some fake thing1\n" (:body asset))
            (should= "test3.fake-output" (:logical-path asset))
            (should= "/test3.fake-output" (asset-path @fake1-compiler-config "test3.fake-output"))
            (should= "/test3.fake-output" (asset-path @fake1-compiler-config "test3" "fake-output"))))

        (defn test-compiler [config body filename input-extension output-extension]
          (str body "compiled with " filename ":" input-extension ":" output-extension))

        (with test-compiler-config (add-compiler-config
                                     @config
                                     (configure-compiler
                                       (set-compiler test-compiler)
                                       (add-input-extension "fake1")
                                       (add-output-extension "fake-output"))))

        (it "compiles the asset"
          (prepare-asset @test-compiler-config "test3.fake-output")
          (let [base-path (directory-path "test_fixtures/public/javascripts")
                asset (find-asset @test-compiler-config "test3.fake-output")]
            (should= (format "Some fake thing1\ncompiled with %s:fake1:fake-output" (str base-path "/test3.fake1")) (:body asset))
            (should= "test3.fake-output" (:logical-path asset))
            (should= "/test3.fake-output" (asset-path @test-compiler-config "test3.fake-output"))
            (should= "/test3.fake-output" (asset-path @test-compiler-config "test3" "fake-output"))))

        (it "does not find assets using compiler extensions when compile is disabled"
          (let [config (set-compile @test-compiler-config false)
                _ (prepare-asset config "test3.fake1")
                asset (find-asset config "test3.fake1")]
            (should-be-nil (find-asset config "test3.fake-output"))
            (should= "Some fake thing1\n" (:body asset))
            (should= "test3.fake1" (:logical-path asset))
            (should= "/test3.fake1" (asset-path config "test3.fake1"))
            (should= "/test3.fake1" (asset-path config "test3" "fake1"))))

        (it "does not find assets using compiler extensions when the pipeline is disabled"
          (let [config (set-pipeline-enabled @test-compiler-config false)
                _ (prepare-asset config "test3.fake1")
                asset (find-asset config "test3.fake1")]
            (prepare-asset config "test1.js")
            (should-be-nil (find-asset config "test3.fake-output"))
            (should= "Some fake thing1\n" (:body asset))
            (should= "test3.fake1" (:logical-path asset))
            (should= "/test3.fake1" (asset-path config "test3.fake1"))
            (should= "/test3.fake1" (asset-path config "test3" "fake1"))))

        (defn test-compressor [config body filename]
          (str body "compressed"))

        (with test-compressor-config (-> @config
                                       (set-compression true)
                                       (add-compressor-config
                                         (configure-compressor
                                           (set-compressor test-compressor)
                                           (set-input-extension "js")))))

        (it "compresses the asset"
          (let [config @test-compressor-config
                _ (prepare-asset config "test1.js")
                asset (find-asset config "test1.js")]
            (should= "var test = 1;\ncompressed" (:body asset))))

        (it "only uses the compresses that matches the extension"
          (let [config @test-compressor-config
                _ (prepare-asset config "test2.fake")
                asset (find-asset config "test2.fake")]
            (should= "Some fake thing\n" (:body asset))))

        (it "does not compress the asset when disabled"
          (let [config (set-compression @test-compressor-config false)
                _ (prepare-asset config "test1.js")
                asset (find-asset config "test1.js")]
            (should= "var test = 1;\n" (:body asset))))

        (it "does not compress the asset when the pipeline is disabled"
          (let [config (set-pipeline-enabled @test-compressor-config false)
                _ (prepare-asset config "test1.js")
                asset (find-asset config "test1.js")]
            (should= "var test = 1;\n" (:body asset))))

        (it "a compiler with multiple extensions"
            (let [configured-compiler (add-compiler-config @config (configure-compiler
                                                                     (add-input-extension "fake")
                                                                     (add-input-extension "fake2")
                                                                     (add-output-extension "fake-output")))]
              (prepare-asset configured-compiler "test1.js")
              (should-be-nil (find-asset configured-compiler "test2.bad-ext"))))

        (with markdown-config (add-compiler-config
                                @config
                                (configure-compiler
                                  (add-input-extension "markdown")
                                  (add-output-extension "html")
                                  (add-output-extension "txt"))))

        (it "a compiler with multiple output extensions"
          (prepare-asset @markdown-config ["multiple_outputs.html" "multiple_outputs.txt"])
          (let [html-asset (find-asset @markdown-config "multiple_outputs.html")
                txt-asset (find-asset @markdown-config "multiple_outputs.txt")]
            (should= "Multiple outputs\n" (:body html-asset))
            (should= "multiple_outputs.html" (:logical-path html-asset))
            (should= "/multiple_outputs.html" (asset-path @markdown-config "multiple_outputs.html"))
            (should= "/multiple_outputs.html" (asset-path @markdown-config "multiple_outputs" "html"))
            (should= "Multiple outputs\n" (:body txt-asset))
            (should= "multiple_outputs.txt" (:logical-path txt-asset))
            (should= "/multiple_outputs.txt" (asset-path @markdown-config "multiple_outputs.txt"))
            (should= "/multiple_outputs.txt" (asset-path @markdown-config "multiple_outputs" "txt"))))

        (with markdown-html-config (configure-compiler
                                     (add-input-extension "markdown")
                                     (add-output-extension "html")))

        (with markdown-txt-config (configure-compiler
                                    (add-input-extension "markdown")
                                    (add-output-extension "txt")))

        (it "mutliple compilers match on input type but only one matches on output type"
          (let [html-markdown (add-compiler-config @config @markdown-html-config)
                configured-compiler (add-compiler-config html-markdown @markdown-txt-config)
                _ (prepare-asset configured-compiler ["multiple_outputs.html" "multiple_outputs.txt"])
                html-asset (find-asset configured-compiler "multiple_outputs.html")
                txt-asset (find-asset configured-compiler "multiple_outputs.txt")]
            (should= "Multiple outputs\n" (:body html-asset))
            (should= "multiple_outputs.html" (:logical-path html-asset))
            (should= "/multiple_outputs.html" (asset-path configured-compiler "multiple_outputs.html"))
            (should= "/multiple_outputs.html" (asset-path configured-compiler "multiple_outputs" "html"))
            (should= "Multiple outputs\n" (:body txt-asset))
            (should= "multiple_outputs.txt" (:logical-path txt-asset))
            (should= "/multiple_outputs.txt" (asset-path configured-compiler "multiple_outputs.txt"))
            (should= "/multiple_outputs.txt" (asset-path configured-compiler "multiple_outputs" "txt"))))

        (it "finds an asset using the digest path"
          (prepare-asset @markdown-config "test1.js")
          (let [asset (find-asset @config "test1.js")]
            (should= asset (find-asset @config (:digest-path asset)))))

        (it "returns nil if the digest does not match"
          (prepare-asset @markdown-config "test1.js")
          (should-be-nil (find-asset @config "test1-200368af90cc4c6f4f1ddf36f97a2bad.js")))

        (with coffeescript-config (add-compiler-config
                                    @config
                                    (configure-compiler
                                      (add-input-extension "coffee")
                                      (add-output-extension "js"))))

        (it "finds an index file"
          (prepare-asset @coffeescript-config "test6.js")
          (let [asset (find-asset @coffeescript-config "test6.js")]
            (should= "var index = 1;\n" (:body asset))
            (should= "test6.js" (:logical-path asset))
            (should= "/test6.js" (asset-path @coffeescript-config "test6.js"))
            (should= "/test6.js" (asset-path @coffeescript-config "test6" "js"))))

        (it "finds an index file with dots in the directory name"
          (prepare-asset @coffeescript-config "test.6.js")
          (let [asset (find-asset @coffeescript-config "test.6.js")]
            (should= "var index6 = 1;\n" (:body asset))
            (should= "test.6.js" (:logical-path asset))
            (should= "/test.6.js" (asset-path @coffeescript-config "test.6" "js"))
            (should= "/test.6.js" (asset-path @coffeescript-config "test.6.js"))))

        (it "finds an index file with a matching output extension"
          (prepare-asset @coffeescript-config "test7.js")
          (let [asset (find-asset @coffeescript-config "test7.js")]
            (should= "var test7 = 1;\n" (:body asset))
            (should= "test7.js" (:logical-path asset))
            (should= "/test7.js" (asset-path @coffeescript-config "test7.js"))
            (should= "/test7.js" (asset-path @coffeescript-config "test7" "js"))))

        ))

    (context "on the load path"
      (it-finds-assets :dynamic (fn [_ _] )))

    (context "in the output path"
      (it-finds-assets :static #(precompile
                                  (set-search-strategy %1 :dynamic) (flatten [%2]))))
    )

  (context "asset-path"

    (defn- it-finds-the-asset-path [search-strategy prepare-asset]

      (list

        (with config (thread-pipeline-config
                       (set-search-strategy search-strategy)
                       (add-directory-to-load-path "test_fixtures/public/javascripts")))

        (after
          (FileUtils/deleteDirectory (file (:output-dir @config))))

        (it "returns the logical path"
          (prepare-asset @config "test1.js")
          (should= "/test1.js" (asset-path @config "test1.js")))

        (it "throws if the file does not exist"
          (prepare-asset @config "test1.js")
          (should-throw
            Exception
            "Asset not found: unknown.js"
            (asset-path @config "unknown.js")))

        (it "returns the logical path with an given output extension"
          (prepare-asset @config "test1.js")
          (should= "/test1.js" (asset-path @config "test1" "js")))

        (with coffeescript-config (add-compiler-config
                                    @config
                                    (configure-compiler
                                      (add-input-extension "coffee")
                                      (add-output-extension "js"))))

        (it "returns the logical path of an asset with dots in the name"
          (prepare-asset @coffeescript-config "test.6.js")
          (should= "/test.6.js" (asset-path @coffeescript-config "test.6" "js")))

        (it "appends the prefix"
          (let [config (add-prefix @config "/assets")]
            (prepare-asset config "test1.js")
            (should= "/assets/test1.js" (asset-path config "test1.js"))))

        (it "appends a leading '/' to the prefix if it doesn't have one"
          (let [config (add-prefix @config "assets")]
            (prepare-asset config "test1.js")
            (should= "/assets/test1.js" (asset-path config "test1.js"))))

        (it "uses the digest path if configured to"
          (let [config (set-use-digest-path @config true)]
            (prepare-asset config "test1.js")
            (should= "/test1-200368af90cc4c6f4f1ddf36f97a279e.js" (asset-path config "test1.js"))))

        ))

    (context "on the load path"
      (it-finds-the-asset-path :dynamic (fn [_ _] )))

    (context "in the output path"
      (it-finds-the-asset-path :static #(precompile
                                          (set-search-strategy %1 :dynamic) (flatten [%2]))))

    )

  (context "asset-url"

    (with config (thread-pipeline-config
                   (set-asset-host "http://cloudfront.net")
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "returns the logical path"
      (should= "http://cloudfront.net/test1.js" (asset-url @config "test1.js")))

    (it "returns nil if the file does not exist"
      (should-throw
        Exception
        "Asset not found: unknown.js"
        (asset-url @config "unknown.js")))

    (it "returns the path if the host is nil"
      (let [config (set-asset-host @config nil)]
        (should= "/test1.js" (asset-url config "test1.js"))))

    (it "returns the logical path with an given output extension"
      (should= "http://cloudfront.net/test1.js" (asset-url @config "test1" "js")))

    (it "appends the prefix"
      (let [config (add-prefix @config "/assets")]
        (should= "http://cloudfront.net/assets/test1.js" (asset-url config "test1.js"))))

    (it "removes trailing '/' from host"
      (let [config (set-asset-host @config "http://cloudfront.net/")]
        (should= "http://cloudfront.net/test1.js" (asset-url config "test1.js"))))

    (it "uses the digest path if configured to"
      (let [config (set-use-digest-path @config true)]
        (should= "http://cloudfront.net/test1-200368af90cc4c6f4f1ddf36f97a279e.js" (asset-url config "test1.js"))))

    )

  (context "wrap-asset-pipeline middleware"

    (with delayed-config
          (delay (thread-pipeline-config
                   (add-directory-to-load-path "test_fixtures/public/javascripts")
                   (add-directory-to-load-path "test_fixtures/public/images")
                   (add-directory-to-load-path "test_fixtures/public/stylesheets"))))

    (with config (thread-pipeline-config
                   (add-directory-to-load-path "test_fixtures/public/javascripts")
                   (add-directory-to-load-path "test_fixtures/public/images")
                   (add-directory-to-load-path "test_fixtures/public/stylesheets")))

    (it "responds with the body of a javascript file when found"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (find-asset @config "test1.js")]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "application/javascript"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/test1.js")))))

    (it "accepts a delayed config"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @delayed-config)
            expected-asset (find-asset @@delayed-config "test1.js")]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "application/javascript"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/test1.js")))))

    (it "serves assets with prefix"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) (add-prefix @config "/assets"))
            expected-asset (find-asset @config "test1.js")]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "application/javascript"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/assets/test1.js")))))

    (it "detects the content type of a css file"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (find-asset @config "test2.css")]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "text/css"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/test2.css")))))

    (it "reads a png file"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (find-asset @config "joodo.png")]
        (should=
          {:status 200
           :headers {"Content-Length" "6533"
                     "Content-Type" "image/png"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/joodo.png")))))

    (it "reads a resource png file"
      (let [config (thread-pipeline-config
                     (add-resource-directory-to-load-path "images" "joodo.png"))
            handler (wrap-asset-pipeline (fn [_] :not-found) config)
            expected-asset (find-asset config "joodo.png")]
        (should=
          {:status 200
           :headers {"Content-Length" "6533"
                     "Content-Type" "image/png"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/joodo.png")))))

    (it "calls the next handler when the asset is not found"
      (let [handler (wrap-asset-pipeline (fn [_] :next-handler-called) @config)]
        (should=
          :next-handler-called
          (handler (mr/request :get "/unknown.js")))))

    )

  (context "precompile"

    (with config (thread-pipeline-config
                   (set-output-dir "test_output")
                   (add-directory-to-load-path "test_fixtures/public/images")
                   (add-directory-to-load-path "test_fixtures/public/javascripts")
                   (add-directory-to-load-path "test_fixtures/public/stylesheets")))

    (after
      (FileUtils/deleteDirectory (file (:output-dir @config)))
      (FileUtils/deleteDirectory (file "test_manifest_dir")))

    (it "writes the asset to the output directory"
      (precompile @config ["test1.js"])
      (should= "var test = 1;\n" (slurp "test_output/test1.js")))

    (it "throws an exception if the asset is not found"
      (should-throw
        Exception
        "Asset not found: unknown.js"
        (precompile @config ["unknown.js"])))

    (it "includes the prefix in the file name"
      (let [config (add-prefix @config "/assets")]
        (precompile config ["test1.js"])
        (should= "var test = 1;\n" (slurp "test_output/assets/test1.js"))))

    (it "compiles two files"
      (precompile @config ["test1.js" "test2.css"])
      (should= "var test = 1;\n" (slurp "test_output/test1.js"))
      (should= ".test2 { color: black; }\n" (slurp "test_output/test2.css")))

    (it "compiles a png file"
      (precompile @config ["joodo.png"])
      (let [png-content (read-file "test_output/joodo.png")]
        (should= 6533 (count png-content))))

    (it "writes the digest file"
      (precompile @config ["test1.js" "test2.css"])
      (should= "var test = 1;\n" (slurp "test_output/test1-200368af90cc4c6f4f1ddf36f97a279e.js"))
      (should= ".test2 { color: black; }\n" (slurp "test_output/test2-9d7e7252425acc78ff419cf3d37a7820.css")))

    (with manifest-output
          {"test2.css" {:logical-path "test2.css"
                        :digest-path "test2-9d7e7252425acc78ff419cf3d37a7820.css"
                        :digest "9d7e7252425acc78ff419cf3d37a7820"}
           "test1.js" {:logical-path "test1.js"
                       :digest-path "test1-200368af90cc4c6f4f1ddf36f97a279e.js"
                       :digest "200368af90cc4c6f4f1ddf36f97a279e"}})

    (it "writes the manifest file mapping the logical path to the written file"
      (precompile @config ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/manifest.edn"))]
        (should=
          @manifest-output
          manifest)))

    (it "writes the manifest file without the prefix"
      (precompile (add-prefix @config "/assets") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/assets/manifest.edn"))]
        (should=
          @manifest-output
          manifest)))

    (it "writes the manifest that is specified in the config"
      (precompile (set-manifest @config "test_output/test-manifest.edn") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/test-manifest.edn"))]
        (should=
          @manifest-output
          manifest)))

    (it "creates the manifest file in a directory that does not exist"
      (precompile (set-manifest @config "test_manifest_dir/test-manifest.edn") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_manifest_dir/test-manifest.edn"))]
        (should=
          @manifest-output
          manifest)))

    (defn gunzip [file-name]
      (read-stream (GZIPInputStream. (FileInputStream. (file file-name)))))

    (it "gzips the output"
      (precompile @config ["test1.js"])
      (should= "var test = 1;\n" (gunzip "test_output/test1.js.gz")))

    (it "gzips a png file"
      (precompile @config ["joodo.png"])
      (should= 6533 (count (gunzip "test_output/joodo.png.gz")))
      (should= 6470 (count (read-file "test_output/joodo.png.gz"))))

      )

  )

