(ns conveyor.middleware-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :as mr]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [conveyor.core :refer :all]
            [conveyor.config :refer :all]
            [conveyor.file-utils :refer [body-to-string]]
            [conveyor.middleware :refer :all]))

(describe "conveyor.middleware"

  (defn do-handler [handler request]
    (let [wrapped-handler (wrap-file-info handler)]
     (-> (wrapped-handler request)
         (update-in [:body] #(body-to-string %))
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
                   "Content-Type" "text/javascript"}
         :body (body-to-string (:body expected-asset))}
        (do-handler handler (mr/request :get "/test1.js")))))

  (it "responds with the content-type and content-length of a compiled file"
    (let [handler (wrap-asset-pipeline (fn [_] {:body "Test"}) (add-compiler-config @config
                                                                  (configure-compiler
                                                                    (add-input-extension "js")
                                                                    (add-output-extension "css"))))]
      (should=
        {:status 200
         :headers {"Content-Length" "14"
                   "Content-Type" "text/css"}
         :body "var test = 1;\n"}
        (do-handler handler (mr/request :get "/test1.css")))))

  (it "serves assets with prefix"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) (add-prefix @config "/assets"))
          expected-asset (with-pipeline-config @config (find-asset "test1.js"))]
      (should=
        {:status 200
         :headers {"Content-Length" "14"
                   "Content-Type" "text/javascript"}
         :body (body-to-string (:body expected-asset))}
        (do-handler handler (mr/request :get "/assets/test1.js")))))

  (it "detects the content type of a css file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (with-pipeline-config @config (find-asset "test2.css"))]
      (should=
        {:status 200
         :headers {"Content-Length" "25"
                   "Content-Type" "text/css"}
         :body (body-to-string (:body expected-asset))}
        (do-handler handler (mr/request :get "/test2.css")))))

  (it "reads a png file"
    (let [handler (wrap-asset-pipeline (fn [_] :not-found) @config)
          expected-asset (with-pipeline-config @config (find-asset "joodo.png"))]
      (should=
        {:status 200
         :headers {"Content-Length" "6533"
                   "Content-Type" "image/png"}
         :body (body-to-string (:body expected-asset))}
        (do-handler handler (mr/request :get "/joodo.png")))))

  (it "reads a resource png file"
    (let [config (thread-pipeline-config
                   (add-resource-directory-to-load-path "images" "joodo.png"))
          handler (wrap-asset-pipeline (fn [_] :not-found) config)
          expected-asset (with-pipeline-config config (find-asset "joodo.png"))]
      (should=
        {:status 200
         :headers {"Content-Length" "6533"
                   "Content-Type" "image/png"}
         :body (body-to-string (:body expected-asset))}
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
