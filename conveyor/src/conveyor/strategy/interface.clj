(ns conveyor.strategy.interface)

(defprotocol Pipeline
  (get-asset [this path])
  (get-logical-path [this path])
  (get-digest-path [this path]))
