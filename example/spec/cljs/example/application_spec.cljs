(ns example.application-spec
  (:require-macros [specljs.core :refer [describe it should=]])
  (:require [specljs.core]
            [example.application :as app]))

(defn setup []
  (let [body (.querySelector js/document "body")
        div (.createElement js/document "div")]
    (.appendChild body div)
    (.setAttribute div "id" "target")
    div))

(defn click-event []
  (let [event (.createEvent js/document "MouseEvent")]
    (.initEvent event "click")
    event))

(describe "application"
  (it "toggles the div id on click"
    (let [div (setup)]
      (app/listen-for-click!)
      (.dispatchEvent div (click-event))
      (should= "clicked" (.getAttribute div "id"))
      (.dispatchEvent div (click-event))
      (should= "target" (.getAttribute div "id")))))
