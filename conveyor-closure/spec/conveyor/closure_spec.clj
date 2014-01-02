(ns conveyor.closure-spec
  (:require [speclj.core :refer :all]
            [conveyor.core :refer :all]
            [conveyor.config :refer [thread-pipeline-config set-compression]]
            [conveyor.closure :refer :all]))

(describe "conveyor.closure"

  (with config (thread-pipeline-config
                 (set-compression true)
                 (add-directory-to-load-path "test_fixtures")))

  (def whitespace-compressed "var cubes,list,math,num,number,opposite,race,square,__slice=[].slice;number=42;opposite=true;if(opposite)number=-42;square=function(x){return x*x};list=[1,2,3,4,5];math={root:Math.sqrt,square:square,cube:function(x){return x*square(x)}};alert(square(25));")

  (it "compiles by removing whitespace"
    (with-pipeline-config (configure-closure @config {:compilation-level :whitespace-only})
      (should= whitespace-compressed
               (:body (find-asset "test.js")))))

  (def simple-compressed "var cubes,list,math,num,number,opposite,race,square,__slice=[].slice;number=42;opposite=!0;number=-42;square=function(a){return a*a};list=[1,2,3,4,5];math={root:Math.sqrt,square:square,cube:function(a){return a*square(a)}};alert(square(25));")

  (it "compiles with simple optimizations"
    (with-pipeline-config (configure-closure @config {:compilation-level :simple-optimizations})
      (should= simple-compressed
               (:body (find-asset "test.js")))))

  (def advanced-compressed "alert(625);")

  (it "compiles with advanced optimizations"
    (with-pipeline-config (configure-closure @config {:compilation-level :advanced-optimizations})
      (should= advanced-compressed
               (:body (find-asset "test.js")))))

  (it "defaults to whitespace removal"
    (with-pipeline-config (configure-closure @config)
      (should= whitespace-compressed
               (:body (find-asset "test.js"))))
    (with-pipeline-config (configure-closure @config {})
      (should= whitespace-compressed
               (:body (find-asset "test.js")))))

  )
