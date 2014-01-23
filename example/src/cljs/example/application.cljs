(ns example.application)

(defn toggle-div [event]
  (let [div (.-toElement event)]
    (if (= "clicked" (.getAttribute div "id"))
      (.setAttribute div "id" "target")
      (.setAttribute div "id" "clicked"))))

(defn listen-for-click! []
  (let [div (.getElementById js/document "target")]
    (.addEventListener div "click" toggle-div)))

(set! (.-onload js/window) listen-for-click!)
