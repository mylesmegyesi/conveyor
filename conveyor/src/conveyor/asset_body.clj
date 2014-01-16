(ns conveyor.asset-body
  (:require [digest]
            [conveyor.time-utils :refer [time-to-date-string]])
  (:import [java.io File]
           [java.net URL]))

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
    (slurp this))

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
    (slurp (.getInputStream (.openConnection this))))
)
