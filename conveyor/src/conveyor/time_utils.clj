(ns conveyor.time-utils
  (:import [java.text SimpleDateFormat ParseException]
           [java.util Date Locale TimeZone]))

(def http-date-formats
  {:rfc1123 "EEE, dd MMM yyyy HH:mm:ss zzz"
   :rfc1036 "EEEE, dd-MMM-yy HH:mm:ss zzz"
   :asctime "EEE MMM d HH:mm:ss yyyy"})

(defn date-format [format]
  (doto (SimpleDateFormat. (format http-date-formats) Locale/US)
     (.setTimeZone (TimeZone/getTimeZone "GMT"))))

(defn format-date [^Date date]
    (.format (date-format :rfc1123) date))

(defn time-to-date-string [time-value]
  (-> (/ time-value 1000)
      (long)
      (* 1000)
      (Date.)
      (format-date)))

(defn string-to-date [date-string]
  (reduce
    (fn [parsed format]
      (or (try
            (.parse (date-format format) date-string)
          (catch ParseException e nil))
          parsed))
    nil
    (keys http-date-formats)))

