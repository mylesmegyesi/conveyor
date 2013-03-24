(ns conveyor.core-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :as mr]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]))

(describe "conveyor.core"

  (context "resource-directory-path"

    (it "builds the full path to a jar resource directory"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")]
        (should= ".test1 { color: white; }\n" (slurp (str full-path "/test1.css")))))

    (it "returns nil when the directory does not exist"
      (should-be-nil (resource-directory-path "non_existant_dir" "test1.css")))

    (it "returns nil when given resources does not exist"
      (should-be-nil (resource-directory-path "stylesheets" "unknown")))

    )

  (context "directory-path"

    (it "builds the full path to a directory"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")]
        (should= ".test2 { color: black; }\n" (slurp (str full-path "/test2.css")))))

    (it "returns nil when the directory does not exist"
      (should-be-nil (directory-path "non_existant_dir")))

    )

  (context "add resource directory to load path"

    (it "adds valid resource directory to the load path"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")
            new-config (add-resource-directory-to-load-path {:load-paths []} "stylesheets" "test1.css")]
        (should= [full-path] (:load-paths new-config))))

    (it "throws an exception when the resource directory does not exist"
      (should-throw IllegalArgumentException "Could not find resource directory: uknown-dir"
                    (add-resource-directory-to-load-path {:load-paths []} "uknown-dir" "test1.css")))

    )

  (context "add directory to load path"

    (it "adds valid directory to the load path"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            new-config (add-directory-to-load-path {:load-paths []} "test_fixtures/public/stylesheets")]
        (should= [full-path] (:load-paths new-config))))

    (it "throws an exception when the directory does not exist"
      (should-throw IllegalArgumentException "Could not find directory: uknown-dir"
                    (add-directory-to-load-path {:load-paths []} "uknown-dir")))

    )

  (context "find-asset"

    (with config (configure-asset-pipeline
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "throws an exception if the extension of the file and requested output extension do not match"
      (should-throw
        Exception
        "The extension of the asset \"test.js\" does not match the requested output extension, \"css\""
        (find-asset @config "test.js" "css")))

    (it "finds an asset and returns the body"
      (let [found-assets (find-asset @config "test1.js")
            asset (first found-assets)]
        (should= 1 (count found-assets))
        (should= "var test = 1;\n" (:body asset))))

    (it "returns the logical path"
      (let [asset (first (find-asset @config "test1.js"))]
        (should= "test1.js" (:logical-path asset))
        (should= asset (first (find-asset @config (:logical-path asset))))))

    (it "returns the digest and digest path"
      (let [asset (first (find-asset @config "test1.js"))]
        (should= "200368af90cc4c6f4f1ddf36f97a279e" (:digest asset))
        (should= "test1-200368af90cc4c6f4f1ddf36f97a279e.js" (:digest-path asset))))

    (it "finds an asset with multiple load paths"
      (let [found-assets (find-asset {:load-paths ["test_fixtures/public/javascripts"
                                                   "test_fixtures/public/stylesheets"]} "test2.css")
            asset (first found-assets)]
        (should= 1 (count found-assets))
        (should= ".test2 { color: black; }\n" (:body asset))
        (should= "test2.css" (:logical-path asset))
        (should= "test2-9d7e7252425acc78ff419cf3d37a7820.css" (:digest-path asset))))

    (it "finds an asset with a resource directory on its load path"
      (let [stylesheets-path (resource-directory-path "stylesheets" "test1.css")
            found-assets (find-asset {:load-paths [stylesheets-path]} "test1.css")
            asset (first found-assets)]
        (should= 1 (count found-assets))
        (should= ".test1 { color: white; }\n" (:body asset))
        (should= "test1.css" (:logical-path asset))
        (should= "test1-89df887049f959cbe331b1da471f7e24.css" (:digest-path asset))))

    (it "returns nil if the asset could not be found"
      (should-be-nil (find-asset {} "non-existant-file")))

    (it "finds assets using the output extensions given by compilers"
      (let [asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (add-input-extension "fake")
                                                                  (add-output-extension "fake-output"))) "test2.fake-output"))]
        (should= "Some fake thing\n" (:body asset))
        (should= "test2.fake-output" (:logical-path asset))
        (should= "test2-979d812cfd0a7dc744af9e083a63ff10.fake-output" (:digest-path asset))))

    (it "file that does not have an extension and matches an compiler with one output extension"
      (let [asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                          (add-input-extension "fake")
                                                                          (add-output-extension "fake-output"))) "test2"))]
        (should= "Some fake thing\n" (:body asset))
        (should= "test2.fake-output" (:logical-path asset))
        (should= "test2-979d812cfd0a7dc744af9e083a63ff10.fake-output" (:digest-path asset))))

    (it "returns nil if the file is found, but the requested file extension does not match any compilers output extensions"
      (should-be-nil (first (find-asset (add-compiler-config @config (configure-compiler
                                                                       (add-input-extension "fake")
                                                                       (add-output-extension "fake-output"))) "test2.bad-ext"))))

    (it "finds an asset using any of an compilers extensions"
      (let [asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (add-input-extension "fake")
                                                                  (add-input-extension "fake1")
                                                                  (add-output-extension "fake-output"))) "test3.fake-output"))]
        (should= "Some fake thing1\n" (:body asset))
        (should= "test3.fake-output" (:logical-path asset))))

    (it "throws an exception if an compiler has two extensions and a file for each extension is found"
      (let [base-path (directory-path "test_fixtures/public/javascripts")]
        (should-throw
          Exception (format "Search for \"test4.fake-output\" returned multiple results: \"%s\", \"%s\""
                            (str base-path "/test4.fake")
                            (str base-path "/test4.fake1"))
          (find-asset (add-compiler-config @config (configure-compiler
                                                   (add-input-extension "fake")
                                                   (add-input-extension "fake1")
                                                   (add-output-extension "fake-output"))) "test4.fake-output"))))

    (it "returns the asset if an compiler has two extensions and a file for each extension is found and a file with the correct output extension is found"
      (let [asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (add-input-extension "fake")
                                                                  (add-input-extension "fake1")
                                                                  (add-output-extension "fake-output"))) "test5.fake-output"))]
        (should= "test5 fake-output\n" (:body asset))
        (should= "test5.fake-output" (:logical-path asset))))

    (it "an compiler with multiple extensions"
      (let [configured-compiler (add-compiler-config @config (configure-compiler
                                                           (add-input-extension "fake")
                                                           (add-input-extension "fake2")
                                                           (add-output-extension "fake-output")))]
      (should-be-nil (first (find-asset configured-compiler "test2.bad-ext")))))

    (it "an compiler with multiple output extensions"
      (let [configured-compiler (add-compiler-config @config (configure-compiler
                                                           (add-input-extension "markdown")
                                                           (add-output-extension "html")
                                                           (add-output-extension "txt")))
            html-asset (first (find-asset configured-compiler "multiple_outputs.html"))
            txt-asset (first (find-asset configured-compiler "multiple_outputs.txt"))]
        (should= "Multiple outputs\n" (:body html-asset))
        (should= "multiple_outputs.html" (:logical-path html-asset))
        (should= "Multiple outputs\n" (:body txt-asset))
        (should= "multiple_outputs.txt" (:logical-path txt-asset))))

    (it "file that does not have an extension and matches an compiler with more than one output extension - must request using an extension"
      (let [base-path (directory-path "test_fixtures/public/javascripts")
            configured-compiler (add-compiler-config @config (configure-compiler
                                                           (add-input-extension "markdown")
                                                           (add-output-extension "html")
                                                           (add-output-extension "txt")))
            html-asset (first (find-asset configured-compiler "multiple_outputs" "html"))
            txt-asset (first (find-asset configured-compiler "multiple_outputs" "txt"))]
        (should= "Multiple outputs\n" (:body html-asset))
        (should= "multiple_outputs.html" (:logical-path html-asset))
        (should= "Multiple outputs\n" (:body txt-asset))
        (should= "multiple_outputs.txt" (:logical-path txt-asset))
        (should-throw
          Exception
          (format "Search for \"multiple_outputs\" found \"%s\". However, you did not request an output extension and the matched compiler has multiple output extensions: html, txt"
                  (str base-path "/multiple_outputs.markdown"))
          (find-asset configured-compiler "multiple_outputs"))))

    (it "mutliple compilers match on input type but only one matches on output type"
      (let [first-configured-compiler (add-compiler-config @config (configure-compiler
                                                           (add-input-extension "markdown")
                                                           (add-output-extension "html")))
            configured-compiler (add-compiler-config first-configured-compiler (configure-compiler
                                                                           (add-input-extension "markdown")
                                                                           (add-output-extension "txt")))
            html-asset (first (find-asset configured-compiler "multiple_outputs.html"))
            txt-asset (first (find-asset configured-compiler "multiple_outputs.txt"))]
        (should= "Multiple outputs\n" (:body html-asset))
        (should= "multiple_outputs.html" (:logical-path html-asset))
        (should= "Multiple outputs\n" (:body txt-asset))
        (should= "multiple_outputs.txt" (:logical-path txt-asset))))

    (it "mutliple compilers match on input and output type"
      (let [first-configured-compiler (add-compiler-config @config (configure-compiler
                                                           (add-input-extension "markdown")
                                                           (add-output-extension "html")))
            configured-compiler (add-compiler-config first-configured-compiler (configure-compiler
                                                                           (add-input-extension "markdown")
                                                                           (add-output-extension "html")))]
        (should-throw
          Exception
          "Found multiple compilers to handle input extension \"markdown\" and output extension \"html\""
          (find-asset configured-compiler "multiple_outputs.html"))))

    (it "finds an asset using the digest path"
      (let [asset (first (find-asset @config "test1.js"))]
        (should= asset (first (find-asset @config (:digest-path asset))))))

    (it "returns nil if the digest does not match"
      (let [asset (first (find-asset @config "test1.js"))]
        (should-be-nil (first (find-asset @config "test1-200368af90cc4c6f4f1ddf36f97a2bad.js")))))

    (it "finds an index file")

    )

  (context "wrap-asset-pipeline middleware"

    (with config (configure-asset-pipeline
                   (add-directory-to-load-path "test_fixtures/public/javascripts")
                   (add-directory-to-load-path "test_fixtures/public/images")
                   (add-directory-to-load-path "test_fixtures/public/stylesheets")))

    (it "responds with the body of a javascript file when found"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (first (find-asset @config "test1.js"))]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "application/javascript"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/test1.js")))))

    (it "serves assets with prefix"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) (add-prefix @config "/assets"))
            expected-asset (first (find-asset @config "test1.js"))]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "application/javascript"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/assets/test1.js")))))

    (it "detects the content type of a css file"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (first (find-asset @config "test2.css"))]
        (should=
          {:status 200
           :headers {"Content-Length" (str (count (:body expected-asset)))
                     "Content-Type" "text/css"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/test2.css")))))

    (it "reads a png file"
      (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
            expected-asset (first (find-asset @config "joodo.png"))]
        (should=
          {:status 200
           :headers {"Content-Length" "6533"
                     "Content-Type" "image/png"}
           :body (:body expected-asset)}
          (handler (mr/request :get "/joodo.png")))))

    (it "reads a resource png file"
      (let [config (configure-asset-pipeline
                     (add-resource-directory-to-load-path "images" "joodo.png"))
            handler (wrap-asset-pipeline (fn [_] :not-found) config)
            expected-asset (first (find-asset config "joodo.png"))]
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


  )
