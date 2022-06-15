# Integreat

Experimental integrant integration library

<!-- badges -->
<!-- [![CircleCI](https://circleci.com/gh/lambdaisland/integreat.svg?style=svg)](https://circleci.com/gh/lambdaisland/integreat) [![cljdoc badge](https://cljdoc.org/badge/lambdaisland/integreat)](https://cljdoc.org/d/lambdaisland/integreat) [![Clojars Project](https://img.shields.io/clojars/v/lambdaisland/integreat.svg)](https://clojars.org/lambdaisland/integreat) -->
<!-- /badges -->

## Rationale and Scope

This library 

- replaces integrant-repl
- provides common boilerplate for dev/test/prod setup of integrant
- pulls settings and secrets (simple key-values, sensitive or not) out of your
  Integrant config
- makes secrets/settings management pluggable (env vars, edn, .env, Vault, etc)
- fail if secret/setting is not present, instead of continuing with `nil`
- allows splitting your integrant config into multiple EDN files

## How it works

Integrant starts from a _config_ map, which then gets used to initialize a
_system_. Integreat introduces one extra concept, the _setup_, which is used to
build up the Integrant config.

```clojure
(ns my-app.system
  (:require [clojure.java.io :as io]))
  
(def setup
  {:settings   [(io/resource "my-app/settings.edn")]
   :secrets    [(io/resource "my-app/secrets.edn")]
   :components [(io/resource "my-app/system.edn")]
   :keys       [:my-app/server]}
```


## License

Copyright &copy; 2022 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.

