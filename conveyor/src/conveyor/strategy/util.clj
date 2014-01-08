(ns conveyor.strategy.util
  (:require [clojure.string :as clj-str]))

(defn remove-asset-digest [path]
  (let [[match digest] (first (re-seq #"(?sm)-([0-9a-f]{7,40})\.[^.]+$" path))]
    (if match
      [digest (clj-str/replace path (str "-" digest) "")]
      [nil path])))

(defn wrap-remove-digest [handler]
  (fn [path config]
    (let [[digest path] (remove-asset-digest path)
          asset (handler path config)]
      (when (or (not digest) (= digest (:digest asset)))
        asset))))

(defn add-suffix [old suffix]
  (if old
    (str old suffix)))

(defn add-asset-suffix [asset suffix]
  (-> (update-in asset [:digest-path] #(add-suffix % suffix))
      (update-in [:logical-path] #(add-suffix % suffix))))

(defn wrap-suffix [handler]
  (fn [path config]
    (let [suffix (re-find #"\?#.*" path)
          path (clj-str/replace path #"\?#.*" "")
          asset (handler path config)]
      (when asset
        (add-asset-suffix asset suffix)))))

