(ns conveyor.finder.interface)

(defprotocol AssetFinder
  (get-asset [this path])
  (get-logical-path [this path])
  (get-digest-path [this path]))

