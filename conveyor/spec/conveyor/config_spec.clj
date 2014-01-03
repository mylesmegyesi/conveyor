(ns conveyor.config-spec
  (:require [speclj.core :refer :all]
            [conveyor.config :refer :all]))

(describe "conveyor.config"

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
