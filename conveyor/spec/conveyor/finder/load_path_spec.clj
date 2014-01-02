(ns conveyor.finder.load-path-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]))

(defn test-compiler [config body filename input-extension output-extension]
  (str body "compiled with " filename ":" input-extension ":" output-extension))

(describe "conveyor.finder.load-path"

  (with config (thread-pipeline-config
                 (set-asset-finder :load-path)
                 (add-directory-to-load-path "test_fixtures/public/javascripts")))

  (with fake1-compiler-config (add-compiler-config
                                @config
                                (configure-compiler
                                  (add-input-extension "fake")
                                  (add-input-extension "fake1")
                                  (add-output-extension "fake-output"))))

  (with markdown-config (add-compiler-config
                          @config
                          (configure-compiler
                            (add-input-extension "markdown")
                            (add-output-extension "html")
                            (add-output-extension "txt"))))

  (with markdown-html-config (configure-compiler
                               (add-input-extension "markdown")
                               (add-output-extension "html")))

  (with coffeescript-config (add-compiler-config
                              @config
                              (configure-compiler
                                (add-input-extension "coffee")
                                (add-output-extension "js"))))

  (it "throws an exception if an compiler has two extensions and a file for each extension is found"
    (with-pipeline-config @fake1-compiler-config
    (let [base-path (directory-path "test_fixtures/public/javascripts")]
      (should-throw
        Exception (format "Search for \"test4.fake-output\" returned multiple results: \"%s\", \"%s\""
                          (str base-path "/test4.fake")
                          (str base-path "/test4.fake1"))
        (find-asset "test4.fake-output")))))

  (it "throws an exception if a compiler has two input extensions and a file for both extensions + an output extension are found"
    (with-pipeline-config @fake1-compiler-config
      (let [base-path (directory-path "test_fixtures/public/javascripts")]
        (should-throw
          Exception (format "Search for \"test5.fake-output\" returned multiple results: \"%s\", \"%s\", \"%s\""
                            (str base-path "/test5.fake-output")
                            (str base-path "/test5.fake")
                            (str base-path "/test5.fake1"))
          (find-asset "test5.fake-output")))))

  (it "file that does not have an extension and matches an compiler with more than one output extension - must request using an extension"
    (with-pipeline-config @markdown-config
      (let [base-path (directory-path "test_fixtures/public/javascripts")]
        (should-throw
          Exception
          (format "Search for \"multiple_outputs\" found \"%s\". However, you did not request an output extension and the matched compiler has multiple output extensions: html, txt"
                  (str base-path "/multiple_outputs.markdown"))
          (find-asset "multiple_outputs")))))

  (it "mutliple compilers match on input and output type"
    (let [html-markdown (add-compiler-config @config @markdown-html-config)
          configured-compiler (add-compiler-config html-markdown @markdown-html-config)]
      (with-pipeline-config configured-compiler
        (should-throw
          Exception
          "Found multiple compilers to handle input extension \"markdown\" and output extension \"html\""
          (find-asset "multiple_outputs.html")))))

  (it "throws an exception if a normal file and index file are both found"
    (let [base-path (directory-path "test_fixtures/public/javascripts")]
      (with-pipeline-config @coffeescript-config
        (should-throw
          Exception (format "Search for \"test8\" returned multiple results: \"%s\", \"%s\""
                            (str base-path "/test8.js")
                            (str base-path "/test8/index.js"))
          (find-asset "test8")))))

  (it "finds assets using compiler extensions when compile is disabled"
    (with-pipeline-config (set-compile @fake1-compiler-config false)
      (let [asset (find-asset "test3.fake1")]
        (should (find-asset "test3.fake-output"))
        (should= "Some fake thing1\n" (:body asset))
        (should= "test3.fake1" (:logical-path asset)))))

  (it "finds assets using compiler extensions when the pipeline is disabled"
    (with-pipeline-config (set-pipeline-enabled @fake1-compiler-config false)
      (let [asset (find-asset "test3.fake1")]
        (should (find-asset "test3.fake-output"))
        (should= "Some fake thing1\n" (:body asset))
        (should= "test3.fake1" (:logical-path asset)))))
  )
