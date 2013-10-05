(ns example.hello
  (:require [example1.hello1 :refer [hello-world!]]))

(defn ^:export my-main []
  (hello-world!))

(hello-world!)
