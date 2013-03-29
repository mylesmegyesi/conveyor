(ns conveyor.core-spec
  (:require [speclj.core :refer :all]
            [ring.mock.request :as mr]
            [conveyor.config :refer :all]
            [conveyor.core :refer :all]))

(describe "conveyor.core"

  (context "asset-path"

    (with config (thread-pipeline-config
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "returns the logical path"
      (should= ["/test1.js"] (asset-path @config "test1.js")))

    (it "returns the logical path with an given output extension"
      (should= ["/test1.js"] (asset-path @config "test1" "js")))

    (it "appends the prefix"
      (let [config (add-prefix @config "/assets")]
        (should= ["/assets/test1.js"] (asset-path config "test1.js"))))

    (it "appends a leading '/' to the prefix if it doesn't have one"
      (let [config (add-prefix @config "assets")]
        (should= ["/assets/test1.js"] (asset-path config "test1.js"))))

    (it "uses the digest path if configured to"
      (let [config (set-use-digest-path @config true)]
        (should= ["/test1-200368af90cc4c6f4f1ddf36f97a279e.js"] (asset-path config "test1.js"))))

    )

  (context "asset-url"

    (with config (thread-pipeline-config
                   (set-asset-host "http://cloudfront.net")
                   (add-directory-to-load-path "test_fixtures/public/javascripts")))

    (it "returns the logical path"
      (should= ["http://cloudfront.net/test1.js"] (asset-url @config "test1.js")))

    (it "returns the path if the host is nil"
      (let [config (set-asset-host @config nil)]
        (should= ["/test1.js"] (asset-url config "test1.js"))))

    (it "returns the logical path with an given output extension"
      (should= ["http://cloudfront.net/test1.js"] (asset-url @config "test1" "js")))

    (it "appends the prefix"
      (let [config (add-prefix @config "/assets")]
        (should= ["http://cloudfront.net/assets/test1.js"] (asset-url config "test1.js"))))

    (it "removes trailing '/' from host"
      (let [config (set-asset-host @config "http://cloudfront.net/")]
        (should= ["http://cloudfront.net/test1.js"] (asset-url config "test1.js"))))

    (it "uses the digest path if configured to"
      (let [config (set-use-digest-path @config true)]
        (should= ["http://cloudfront.net/test1-200368af90cc4c6f4f1ddf36f97a279e.js"] (asset-url config "test1.js"))))

    )

  (context "wrap-asset-pipeline middleware"

    (with config (thread-pipeline-config
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
      (let [config (thread-pipeline-config
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
