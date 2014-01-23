# Conveyor Example App
This is an example web app using [Conveyor](https://github.com/mylesmegyesi/conveyor). This app demonstrates a basic setup using Conveyor to compile ClojureScript and Sass.

## Usage
To use the runtime strategy, in a development environment:

```bash
$ lein ring server
```
To use the precompiled strategy:

First, run the precompile task, which can be found in src/example/precompile.clj

```bash
$ lein precompile-assets
```

Then, start the server with `PRODUCTION=true`, which will set the pipeline strategy to `:precompiled`

```bash
$ PRODUCTION=true lein ring server
```

## Testing
```bash
$ lein spec
```
```bash
$ lein cljsbuild once
```
Note: cljsbuild only needs to be used to run the ClojureScript tests. Conveyor will compile any cljs files in the src/cljs/example directory.
