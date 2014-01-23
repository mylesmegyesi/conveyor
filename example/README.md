# Conveyor Example App
This is an example web app using [Conveyor](https://github.com/mylesmegyesi/conveyor). This app demonstrates a basic setup using Conveyor to compile ClojureScript and Sass.

## Usage
```bash
$ lein ring server
```

## Testing
```bash
$ lein spec
```
```bash
$ lein cljsbuild once
```
Note: cljsbuild only needs to be used to run the ClojureScript tests. Conveyor will compile any cljs files in the src/cljs/example directory.
