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

  (context "asset-path"

    (with config (thread-pipeline-config
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "returns the logical path"
      (should= "/test1.js" (asset-path @config "test1.js")))

    (it "returns nil if the file does not exist"
      (should-be-nil (asset-path @config "unknown.js")))

    (it "returns the logical path with an given output extension"
      (should= "/test1.js" (asset-path @config "test1" "js")))

    (it "appends the prefix"
      (let [config (add-prefix @config "/assets")]
        (should= "/assets/test1.js" (asset-path config "test1.js"))))

    (it "appends a leading '/' to the prefix if it doesn't have one"
      (let [config (add-prefix @config "assets")]
        (should= "/assets/test1.js" (asset-path config "test1.js"))))

    (it "uses the digest path if configured to"
      (let [config (set-use-digest-path @config true)]
        (should= "/test1-200368af90cc4c6f4f1ddf36f97a279e.js" (asset-path config "test1.js"))))

    )

  (context "asset-url"

    (with config (thread-pipeline-config
                   (set-asset-host "http://cloudfront.net")
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "returns the logical path"
      (should= "http://cloudfront.net/test1.js" (asset-url @config "test1.js")))

    (it "returns nil if the file does not exist"
      (should-be-nil (asset-url @config "unknown.js")))

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

    (it "writes the digest file when configured"
      (precompile (set-use-digest-path @config true) ["test1.js" "test2.css"])
      (should= "var test = 1;\n" (slurp "test_output/test1-200368af90cc4c6f4f1ddf36f97a279e.js"))
      (should= ".test2 { color: black; }\n" (slurp "test_output/test2-9d7e7252425acc78ff419cf3d37a7820.css")))

    (it "writes the manifest file mapping the logical path to the written file"
      (precompile @config ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/manifest.edn"))]
        (should=
          {"test2.css" "test2.css"
           "test1.js" "test1.js"}
          manifest)))

    (it "writes the manifest file without the prefix"
      (precompile (add-prefix @config "/assets") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/assets/manifest.edn"))]
        (should=
          {"test2.css" "test2.css"
           "test1.js" "test1.js"}
          manifest)))

    (it "writes the manifest file with the digest file name"
      (precompile (set-use-digest-path @config true) ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/manifest.edn"))]
        (should=
          {"test2.css" "test2-9d7e7252425acc78ff419cf3d37a7820.css"
           "test1.js" "test1-200368af90cc4c6f4f1ddf36f97a279e.js"}
          manifest)))

    (it "writes the manifest that is specified in the config"
      (precompile (set-manifest @config "test_output/test-manifest.edn") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_output/test-manifest.edn"))]
        (should=
          {"test2.css" "test2.css"
           "test1.js" "test1.js"}
          manifest)))

    (it "creates the manifest file in a directory that does not exist"
      (precompile (set-manifest @config "test_manifest_dir/test-manifest.edn") ["test1.js" "test2.css"])
      (let [manifest (read-edn (slurp "test_manifest_dir/test-manifest.edn"))]
        (should=
          {"test2.css" "test2.css"
           "test1.js" "test1.js"}
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

