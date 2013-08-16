(ns conveyor.compress)

(defn- compressor-for-extension [config extension]
  (first
    (filter
      #(= extension (:input-extension %))
      (:compressors config))))

(defn compress-asset [config path asset]
  (let [asset-extension (:extension asset)]
    (if-let [compressor (compressor-for-extension config asset-extension)]
      (assoc asset :body ((:compressor compressor)
                            config (:body asset) (:absolute-path asset)))
      asset)))

