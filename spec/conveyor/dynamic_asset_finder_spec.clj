(ns conveyor.dynamic-asset-finder-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.dynamic-asset-finder :refer :all]))

(defn test-compiler [config body filename input-extension output-extension]
  (str body "compiled with " filename ":" input-extension ":" output-extension))

(describe "conveyor.dynamic-asset-finder"

  (with config (thread-pipeline-config
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

  (it "finds an asset and returns the body with a requested output extension"
    (let [found-assets (find-asset @config "test1" "js")
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

  (it "finds an asset using any of the configured compilers extensions"
    (let [asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (add-input-extension "fake")
                                                                  (add-input-extension "fake1")
                                                                  (add-output-extension "fake-output"))) "test3.fake-output"))]
      (should= "Some fake thing1\n" (:body asset))
      (should= "test3.fake-output" (:logical-path asset))))

  (it "compiles the asset"
    (let [base-path (directory-path "test_fixtures/public/javascripts")
          asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (set-compiler test-compiler)
                                                                  (add-input-extension "fake1")
                                                                  (add-output-extension "fake-output"))) "test3.fake-output"))]
      (should= (format "Some fake thing1\ncompiled with %s:fake1:fake-output" (str base-path "/test3.fake1")) (:body asset))
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

  (it "throws an exception if a compiler has two input extensions and a file for both extensions + an output extension are found"
    (let [base-path (directory-path "test_fixtures/public/javascripts")]
      (should-throw
        Exception (format "Search for \"test5.fake-output\" returned multiple results: \"%s\", \"%s\", \"%s\""
                          (str base-path "/test5.fake-output")
                          (str base-path "/test5.fake")
                          (str base-path "/test5.fake1"))
        (find-asset (add-compiler-config @config (configure-compiler
                                                   (add-input-extension "fake")
                                                   (add-input-extension "fake1")
                                                   (add-output-extension "fake-output"))) "test5.fake-output"))))

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

  (it "compiles the asset for a file that has no requested extension and one output extension"
    (let [base-path (directory-path "test_fixtures/public/javascripts")
          asset (first (find-asset (add-compiler-config @config (configure-compiler
                                                                  (set-compiler test-compiler)
                                                                  (add-input-extension "fake1")
                                                                  (add-output-extension "fake-output"))) "test3"))]
      (should= (format "Some fake thing1\ncompiled with %s:fake1:fake-output" (str base-path "/test3.fake1")) (:body asset))
      (should= "test3.fake-output" (:logical-path asset))))

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

  (it "finds an index file"
    (let [config (add-compiler-config @config (configure-compiler
                                                (add-input-extension "coffee")
                                                (add-output-extension "js")))
          found-assets (find-asset config "test6")
          asset (first found-assets)]
      (should= "var index = 1;\n" (:body asset))
      (should= "test6.js" (:logical-path asset))))

  (it "finds an index file with a matching output extension"
    (let [config (add-compiler-config @config (configure-compiler
                                                (add-input-extension "coffee")
                                                (add-output-extension "js")))
          found-assets (find-asset config "test7")
          asset (first found-assets)]
      (should= "var test7 = 1;\n" (:body asset))
      (should= "test7.js" (:logical-path asset))))

  (it "throws an exception if a normal file and index file are both found"
    (let [base-path (directory-path "test_fixtures/public/javascripts")]
      (should-throw
        Exception (format "Search for \"test8\" returned multiple results: \"%s\", \"%s\""
                          (str base-path "/test8.js")
                          (str base-path "/test8/index.js"))
        (find-asset (add-compiler-config @config (configure-compiler
                                                   (add-input-extension "coffee")
                                                   (add-output-extension "js"))) "test8"))))
  )
