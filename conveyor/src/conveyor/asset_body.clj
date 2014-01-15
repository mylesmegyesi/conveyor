(ns conveyor.asset-body
  (:require [digest])
  (:import [java.io File]
           [java.net URL]
           [java.text SimpleDateFormat]
           [java.util Date Locale TimeZone]))

(defn format-date [^Date date]
  (let [date-format (doto (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss ZZZ" Locale/US)
                     (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (.format date-format date)))

(defn time->http-date [-time]
  (-> (/ -time 1000)
      (long)
      (* 1000)
      (Date.)
      (format-date)))

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
    (time->http-date (.lastModified this)))

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
        (time->http-date)))

  (body-to-string [this]
    (slurp (.getInputStream (.openConnection this))))
)
