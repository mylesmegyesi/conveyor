(ns conveyor.middleware-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :as mr]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [conveyor.asset-body :refer [body-to-string response-body]]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.middleware :refer :all]))

(describe "conveyor.middleware"

  (defn do-handler [handler request]
    (let [wrapped-handler (wrap-file-info handler)]
     (-> (wrapped-handler request)
         (update-in [:body] slurp)
         (update-in [:headers] #(dissoc % "Last-Modified")))))

  (with config (thread-pipeline-config
                 (add-directory-to-load-path "test_fixtures/public/javascripts")
                 (add-directory-to-load-path "test_fixtures/public/images")
                 (add-directory-to-load-path "test_fixtures/public/stylesheets")))

  (it "responds with the body of a javascript file when found"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (with-pipeline-config @config (find-asset "test1.js"))]
      (should=
        {:status 200
         :headers {"Content-Length" "14"
                   "Content-Type" "text/javascript"
                   "ETag" (:digest expected-asset)}
         :body (slurp (response-body (:body expected-asset)))}
        (do-handler handler (mr/request :get "/test1.js")))))

  (it "serves assets with prefix"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) (add-prefix @config "/assets"))
          expected-asset (with-pipeline-config @config (find-asset "test1.js"))]
      (should=
        {:status 200
         :headers {"Content-Length" "14"
                   "Content-Type" "text/javascript"
                   "ETag" (:digest expected-asset)}
         :body (slurp (response-body (:body expected-asset)))}
        (do-handler handler (mr/request :get "/assets/test1.js")))))

  (it "detects the content type of a css file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (with-pipeline-config @config (find-asset "test2.css"))]
      (should=
        {:status 200
         :headers {"Content-Length" "25"
                   "Content-Type" "text/css"
                   "ETag" (:digest expected-asset)}
         :body (slurp (response-body (:body expected-asset)))}
        (do-handler handler (mr/request :get "/test2.css")))))

  (it "reads a png file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (with-pipeline-config @config (find-asset "joodo.png"))]
      (should=
        {:status 200
         :headers {"Content-Length" "6533"
                   "Content-Type" "image/png"
                   "ETag" (:digest expected-asset)}
         :body (slurp (response-body (:body expected-asset)))}
        (do-handler handler (mr/request :get "/joodo.png")))))

  (it "reads a resource png file"
    (let [config (thread-pipeline-config
                   (add-resource-directory-to-load-path "images" "joodo.png"))
          handler (wrap-asset-pipeline (fn [_] :not-found) config)
          expected-asset (with-pipeline-config config (find-asset "joodo.png"))]
      (should=
        {:status 200
         :headers {"Content-Length" "6533"
                   "Content-Type" "image/png"
                   "ETag" (:digest expected-asset)}
         :body (slurp (response-body (:body expected-asset)))}
        (do-handler handler (mr/request :get "/joodo.png")))))

  (it "calls the next handler when the asset is not found"
    (let [handler (wrap-asset-pipeline (fn [_] :next-handler-called) @config)]
      (should=
        :next-handler-called
        (handler (mr/request :get "/unknown.js")))))

  (it "binds the *pipeline* when it calls the next handler"
    (let [handler (wrap-asset-pipeline (fn [_] (should (bound? #'*pipeline*))) @config)]
      (handler (mr/request :get "/unknown.js"))))

  (it "initializes the config"
    (let [absolute-stylesheets-path (directory-path "test_fixtures/public/stylesheets")
          stylesheet-path-expanded? (fn [] (some #(= absolute-stylesheets-path %) (:load-paths *pipeline-config*)))
          handler (wrap-pipeline-config (fn [_] (should (stylesheet-path-expanded?))) @config)]
      (handler (mr/request :get "/unknown.js"))))

  )
