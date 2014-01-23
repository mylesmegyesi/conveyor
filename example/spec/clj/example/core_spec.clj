(ns example.core-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :refer [request]]
            [example.core :refer :all]))

(describe "app"
  (it "responds with 200"
    (let [response (handler (request :get "/"))]
      (should= 200 (:status response)))))
