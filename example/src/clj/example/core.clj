(ns example.core
  (:require [compojure.core :refer [defroutes GET]]
            [conveyor.core :refer [asset-url]]
            [conveyor.middleware :refer [wrap-asset-pipeline]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]))

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
    :output-dir "/resources/public"
    :strategy :runtime})

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
    (wrap-asset-pipeline conveyor-config)))
