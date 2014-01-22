# Conveyor
An asset pipeline for Clojure web apps.

Conveyor includes plugins for:
* ClojureScript
* Closure
* CoffeeScript
* Compass
* Sass

Using the `:runtime` strategy, Conveyor finds and compiles assets on each request. If a file in the load-paths can be compiled into the requested file given the configured compilers, an asset-map will be returned.

Using the `:precompiled` strategy, Conveyor finds precompiled assets in the configured `:output-dir` by looking up asset-paths in the manifest. Assets must be precompiled using `precompile`, which writes asset-maps to the manifest and writes compiled files to the `:output-dir`.

`asset-url` returns the url for a compiled asset.

# Installation
## Using Leiningen
Include Conveyor in your `:dependencies`
```clojure
[conveyor "0.2.8"]
```
## Plugins
Include plugins in your `:dev` profile `:dependencies`
```clojure
:profiles {:dev {:dependencies [conveyor-sass "0.2.8"]
                               [conveyor-compass "0.2.8"]
                               [conveyor-clojurescript "0.2.8"]}}
```
## Manual Installation
1. Check out the source code: [https://github.com/mylesmegyesi/conveyor](https://github.com/mylesmegyesi/conveyor)
2. Install it:

```bash
$ rake install
```

# Usage
## Config
```clojure
(def default-pipeline-config
  {:load-paths []
   :cache-dir "target/conveyor-cache"
   :strategy :runtime
   :compilers []
   :compressors []
   :prefix "/"
   :output-dir "public"
   :compress false
   :compile true
   :pipeline-enabled true})
```
* `:load-paths` - The load-paths for your assets
* `:cache-dir` - The cache-dir
* `:strategy` - `:runtime` or `:precompiled`
* `:compilers` - The list of compilers
* `:compressor` - The list of compressors
* `:prefix` - The prefix to append to asset-urls
* `:output-dir` - The output directory for precompiled assets
* `:compress` - Compress assets when true
* `:compile` - Compile assets when true
* `:pipeline-enabled` - No compile or compress when false

Additional Optional Keys
* `:asset-host` - The asset host for assets
* `:use-digest-path` - Appends the md5 digest to the filename when true
* `:manifest` - The directory to write the manifest, otherwise the `:output-dir` + `:prefix` is used

### Example Runtime Config
```clojure
{:load-paths [{:type :directory
               :path "src/assets/scss"}
              {:type :directory
               :path "src/assets/images"}
              {:type :directory
               :path "src/assets/fonts"}
              {:type :resource-directory
               :path "src/assets/files"}
              {:type :directory
               :path "src/assets/javascripts"}
              {:type :directory
               :path "src/cljs"}]
 :use-digest-path false
 :plugins [:sass :compass {:plugin-name :clojurescript
                           :optimizations :whitespace
                           :pretty-print false}]
 :prefix "/assets"
 :output-dir "/resources/public"
 :strategy :runtime}
```

### Example Precompiled Config
```clojure
{:strategy :precompiled
 :pipeline-enabled false
 :prefix "/assets"
 :output-dir "/resources/public"
 :use-digest-path true}
```

## Middleware
### wrap-asset-pipeline
`wrap-asset-pipeline` can be used to serve assets when using the `:runtime` pipeline strategy.
```clojure
(ns sample.core
  (:require [conveyor.middleware :refer [wrap-asset-pipeline]]))

(def config {:strategy :runtime})

(def handler
  (-> app
    (wrap-asset-pipeline config)))
```

### wrap-pipeline-config
`wrap-pipeline-config` can be used to initialize the pipeline with a given config for calls to `asset-url`. `wrap-pipeline-config` does not serve assets, so other middleware like [Ring](https://github.com/ring-clojure/ring) `wrap-file-info` and `wrap-resource` can be used to serve precompiled assets.
```clojure
(ns sample.core
  (:require [conveyor.middleware :refer [wrap-pipeline-config]]))

(def config {:strategy :precompiled})

(def handler
  (-> app
    (wrap-resource "public")
    wrap-file-info
    (wrap-pipeline-config config)))
```

## asset-url
`asset-url` returns the url for an asset name.
```clojure
(ns sample.core
  (:require [conveyor.core :refer [asset-url]]))

(def prefix-config {:load-paths [{:type :directory :path "src/javascripts"}]
                    :prefix "/assets"})

(with-pipeline-config prefix-config
  (asset-url "application.js")  ;=> "/assets/application.js"
  (asset-url "other/index.js")) ;=> "/assets/other/index.js"

(def host-config {:load-paths [{:type :directory :path "src/javascripts"}]
                  :asset-host "http://example.net"})

(with-pipeline-config host-config
  (asset-url "application.js")) ;=> "http://example.net/application.js"

(def digest-path-config {:load-paths [{:type :directory :path "src/javascripts"}]
                         :prefix "/assets"
                         :use-digest-path true})

(with-pipeline-config digest-path-config
  (asset-url "application.js")) ;=> "/assets/application-200368af90cc4c6f4f1ddf36f97a279e.js"
```

## precompile
`precompile` compiles a collection of filenames, or matching files given a regex, to the `:output-dir` and writes each asset-map to a manifest.
```clojure
(ns sample.precompile
  (:require [conveyor.core :refer [with-pipeline-config]]
            [conveyor.precompile :refer [precompile]]))

(def config {:load-paths [{:type :directory :path "src/assets"}]
             :plugins [:sass :compass]})

(def assets ["application.css" "application.js"
             #".*.pdf" #".*.eot" #".*.svg" #".*.ttf" #".*.woff" #".*.jpg" #".*.png"])

(defn -main [& args]
  (with-pipeline-config config
    (precompile assets)))
```

## find-asset
`find-asset` returns a map
```clojure
(ns sample.core
  (:require [conveyor.core :refer [find-asset]]))

(defn config {:use-digest-path true})

(with-pipeline-config config
  (find-asset "test2.css"))
```
```clojure
{:digest-path "test2-9d7e7252425acc78ff419cf3d37a7820.css",
 :digest "9d7e7252425acc78ff419cf3d37a7820",
 :body ".test2 { color: black; }\n",
 :absolute-path "/home/conveyor/conveyor/test_fixtures/public/stylesheets/test2.css",
 :content-length 25
 :extension "css",
 :logical-path "test2.css"}
```
```clojure
(defn config {:use-digest-path false
              :pipeline-enabled false})

(with-pipeline-config config
  (find-asset "test2.css"))
```
```clojure
{:digest "9d7e7252425acc78ff419cf3d37a7820",
 :body #<File test2.css>,
 :absolute-path "/home/conveyor/conveyor/test_fixtures/public/stylesheets/test2.css",
 :content-length 25
 :last-modified "Thu, 01 Jan 2013 00:00:00 GMT"
 :extension "css",
 :logical-path "test2.css"}
```
# Contributing
Conveyor uses [Leiningen](https://github.com/technomancy/leiningen) version 2.0.0 or later.

Clone the master branch, build, and run the library and plugin tests:

```bash
$ git clone https://github.com/mylesmegyesi/conveyor.git
$ cd conveyor
$ rake
```

To run only the conveyor library tests:
```bash
$ cd conveyor
$ lein spec
```

Make patches and submit them along with an issue (see below).

## Issues
Post issues on the conveyor github project:

* [https://github.com/mylesmegyesi/conveyor/issues](https://github.com/mylesmegyesi/conveyor/issues)

# License
Copyright (C) 2013-2014 Myles Megyesi All Rights Reserved.

Distributed under the The MIT License.
