(ns conveyor.asset-body
  (:require [clojure.java.io :refer [input-stream]]
            [digest]
            [conveyor.time-utils :refer [time-to-date-string]])
  (:import [java.io File]
           [java.net URL]))

(defn read-stream [stream]
  (let [sb (StringBuilder.)]
    (with-open [stream stream]
      (loop [c (.read stream)]
        (if (neg? c)
          (str sb)
          (do
            (.append sb (char c))
            (recur (.read stream))))))))

(defprotocol AssetBody
  (response-body [this])
  (md5 [this])
  (content-length [this])
  (last-modified-date [this])
  (body-to-string [this]))

(extend-protocol AssetBody
  String
  (response-body [this]
    this)

  (md5 [this]
    (digest/md5 this))

  (content-length [this]
    (count this))

  (last-modified-date [this]
    nil)

  (body-to-string [this]
    this)

  File
  (response-body [this]
    this)

  (md5 [this]
    (digest/md5 this))

  (content-length [this]
    (.length this))

  (last-modified-date [this]
    (time-to-date-string (.lastModified this)))

  (body-to-string [this]
    (read-stream (input-stream this)))

  URL
  (response-body [this]
    (.getInputStream (.openConnection this)))

  (md5 [this]
    (digest/md5 (.getInputStream (.openConnection this))))

  (content-length [this]
    (.getContentLength (.openConnection this)))

  (last-modified-date [this]
    (-> (.getLastModified (.openConnection this))
        (time-to-date-string)))

  (body-to-string [this]
    (read-stream (.getInputStream (.openConnection this))))
)
