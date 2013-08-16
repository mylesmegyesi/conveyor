(ns conveyor.middleware-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :as mr]
            [conveyor.config :refer :all]
            [conveyor.core :refer [find-asset with-pipeline-config]]
            [conveyor.middleware :refer :all]
            ))

(describe "conveyor.middleware"
  (with delayed-config
        (delay (thread-pipeline-config
                 (add-directory-to-load-path "test_fixtures/public/javascripts")
                 (add-directory-to-load-path "test_fixtures/public/images")
                 (add-directory-to-load-path "test_fixtures/public/stylesheets"))))

  (with config (thread-pipeline-config
                 (add-directory-to-load-path "test_fixtures/public/javascripts")
                 (add-directory-to-load-path "test_fixtures/public/images")
                 (add-directory-to-load-path "test_fixtures/public/stylesheets")))

  (around [it]
    (with-pipeline-config @config
      (it)))

  (it "responds with the body of a javascript file when found"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (find-asset "test1.js")]
      (should=
        {:status 200
         :headers {"Content-Length" (str (count (:body expected-asset)))
                   "Content-Type" "application/javascript"}
         :body (:body expected-asset)}
        (handler (mr/request :get "/test1.js")))))

  (it "accepts a delayed config"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @delayed-config)
          expected-asset (with-pipeline-config @@delayed-config (find-asset "test1.js"))]
      (should=
        {:status 200
         :headers {"Content-Length" (str (count (:body expected-asset)))
                   "Content-Type" "application/javascript"}
         :body (:body expected-asset)}
        (handler (mr/request :get "/test1.js")))))

  (it "serves assets with prefix"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) (add-prefix @config "/assets"))
          expected-asset (find-asset "test1.js")]
      (should=
        {:status 200
         :headers {"Content-Length" (str (count (:body expected-asset)))
                   "Content-Type" "application/javascript"}
         :body (:body expected-asset)}
        (handler (mr/request :get "/assets/test1.js")))))

  (it "detects the content type of a css file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (find-asset "test2.css")]
      (should=
        {:status 200
         :headers {"Content-Length" (str (count (:body expected-asset)))
                   "Content-Type" "text/css"}
         :body (:body expected-asset)}
        (handler (mr/request :get "/test2.css")))))

  (it "reads a png file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (find-asset "joodo.png")]
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
          expected-asset (with-pipeline-config config (find-asset "joodo.png"))]
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
