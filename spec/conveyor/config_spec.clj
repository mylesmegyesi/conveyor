(ns conveyor.config-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]))

(describe "conveyor.config"

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

  (context "configure-asset-pipeline"

    (it "adds a resource directory path to the load path"
      (let [full-path (resource-directory-path "stylesheets" "test1.css")
            config (configure-asset-pipeline {:load-paths [{:type :resource-directory
                                                            :path "stylesheets"
                                                            :file-in-dir "test1.css"}]})]
        (should= [full-path] (:load-paths config))))

    (it "adds a directory path to the load path"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            config (configure-asset-pipeline {:load-paths [{:type :directory
                                                            :path "test_fixtures/public/stylesheets"}]})]
        (should= [full-path] (:load-paths config))))

    (it "throws an exception for an unknown load path type"
      (should-throw
        Exception
        "Unknown type of load-path: :unknown-type. Valid types are :resource-directory and :directory."
        (configure-asset-pipeline {:load-paths [{:type :unknown-type}]})))

    (it "configures the prefix"
      (let [config (configure-asset-pipeline {:prefix "/assets"})]
      (should= "/assets" (:prefix config))))

    (it "configures a plugin"
      (let [full-path (directory-path "test_fixtures/public/stylesheets")
            config (configure-asset-pipeline {:plugins [:test-plugin]})]
        (should= "configured" (:test-config config))
        (should= [full-path] (:load-paths config))))

    (it "configures multiple plugins"
      (let [style-path (directory-path "test_fixtures/public/stylesheets")
            js-path (directory-path "test_fixtures/public/javascripts")
            config (configure-asset-pipeline {:plugins [:test-plugin :other-plugin]})]
        (should= "configured" (:test-config config))
        (should== [style-path js-path] (:load-paths config))))

    )

  )
