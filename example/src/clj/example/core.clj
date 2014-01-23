(ns example.core
  (:require [compojure.core :refer [defroutes GET]]
            [conveyor.core :refer [asset-url]]
            [conveyor.middleware :refer [wrap-asset-pipeline wrap-pipeline-config]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]))

(def conveyor-config
  {:load-paths [{:type :directory
                 :path "src/assets/scss"}
                {:type :directory
                 :path "src/cljs/example"}]
    :use-digest-path false
    :plugins [:sass {:plugin-name :clojurescript
                     :optimizations :whitespace
                     :pretty-print false}]
    :prefix "/assets"
    :output-dir "resources/public"
    :strategy :runtime})

(defn production-config [config]
  (assoc config :strategy :precompiled))

(defn production? []
  (let [production (System/getenv "PRODUCTION")]
    (and production (= production "true"))))

(defn wrap-assets [handler config]
  (if (production?)
    (-> (wrap-resource handler "public")
        wrap-file-info
        (wrap-pipeline-config (production-config config)))
    (wrap-asset-pipeline handler config)))

(defn render-index []
  (html
    [:head
     [:title "Conveyor Example"]
     (include-css (asset-url "application.css"))
     (include-js (asset-url "application.js"))]
    [:body
     [:h1 "Conveyor"]
     [:div#target
      [:span "Click Me"]]]))

(defroutes app
  (GET "/" [] (render-index)))

(def handler
  (-> app
    (wrap-assets conveyor-config)))
