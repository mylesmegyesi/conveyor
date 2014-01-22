# Conveyor
An asset pipeline for Clojure web apps.

Conveyor includes plugins for:
* ClojureScript
* Closure
* CoffeeScript
* Compass
* Sass

`asset-url` returns the url for a compiled asset.
Using the `:runtime` strategy, Conveyor finds and compiles assets on each request.
Using the `:precompiled` strategy, Conveyor finds precompiled assets from the configured `:output-dir`.

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
   :use-digest-path false
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
* `:use-digest-path` - Appends the md5 digest to the filename when true
* `:compress` - Compress assets when true
* `:compile` - Compile assets when true
* `:pipeline-enabled` - No compile or compress when false

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
`wrap-pipeline-config` can be used to serve precompiled assets from the `:output-dir` when using the `:precompiled` strategy.
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

(asset-url "application.js") ;=> "/assets/application.js"
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

(find-asset "test2.css")
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
