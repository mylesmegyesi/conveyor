(ns conveyor.finder.interface)

(defprotocol AssetFinder
  (get-asset [this path extension])
  (get-logical-path [this path extension])
  (get-digest-path [this path extension]))

