(ns conveyor.asset
  (:require [digest :refer [md5]]
            [conveyor.filename-utils :refer :all]))

(defn build-asset [requested-path extension asset-body]
  (let [digest (md5 asset-body)
        file-name (remove-extension requested-path)]
    [{:body asset-body
      :logical-path (add-extension file-name extension)
      :digest digest
      :digest-path (add-extension (str file-name "-" digest) extension)}]))

