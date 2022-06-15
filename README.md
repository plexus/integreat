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

Only `:components` is mandatory, it is a vector of maps or files/resources
(anything readable by Aero). They are all read and merged into a single map,
which becomes your Integrant config.

Currently Integreat will throw if a key is present in multiple component maps,
as a safeguard to make sure you don't accidentally have the same component
config specified in two locations. However it can be useful to deliberately
"layer" these, having later `:components` entries override earlier once, this
could become something you can opt into.

`system.edn` could look like this

```clojure
{:app.http/http
 {:port    #setting :http-port
  :storage #ig/ref :app.persistance/storage-service}
 
 :app.persistance/storage-service
 {:api-key #secret :storage-api-key}}
```

In there you can see regular integrant references (`#ig/ref`), but also
references to _settings_ and _secrets_. These can be read from a separate
Aero-processed EDN file, but they could also come from environment variables, a
`.env` file, Java properties, or a configuration management system like
Hashicorp Vault.

In your setup `:secrets` and `:settings` are vectors which can contain
functions, maps, or files/resources (anything readably by Aero). These are
checked in order to find a certain key. If none of them provides a value for a
key then an error is thrown. This is important, as it catches situations where a
setting/secret was introduced, but not yet configured in a certain environment
(starting with local dev).

We provide a few functions that can be used to provide these settings/secrets.

- `env`: Look up as environment variable.
- `env-edn`: Look up as environment variable, then attempt to parse as EDN. Will return the string value if parsing fails.
- `properties`: Look up as Java properties
- `properties-edn`: Look up as Java properties, then attempt to parse as EDN. Will return the string value if parsing fails.
- `dotenv`: Read and parse a local `.env` file.

`env`, `env-edn`, and `dotenv` will all convert the secret/setting keywords into
an environment variable name using the same conventions: letters get uppercased,
dashes and periods become underscores, a namespace separator (`/`) becomes a
double underscore `__`. Other special characters are munged per
`clojure.core/munge`. If a string is used with `#secret "..."` or `:setting
"...`" then it is assumed to be a valid env var name and is left alone.

- `:foo.bar/baz-baq` => `"FOO_BAR__BAZ_BAQ"`

`properties`/`properties-edn` will drop the initial `:` from keywords, but will
otherwise use the key as-is.

So your setup could look like this:

```clojure
(def setup
  {:components [(io/resource "my-app/system.edn")]
   :settings   [integreat/env
                (io/resource "settings.edn")]
   :secrets    [integreat/env
                (integreat/dotenv ".env" {:expand? true})]})
```

With `settings.edn`

```clojure
{:http-port #profile {:prod 80 :dev 8080 :test 8081}}
```

And `.env`

```shell
STORAGE_API_KEY="api-123abc..."
```

With this `setup` in hand we can now use it to start a dev, test, or prod
system. Integreat provides three namespaces for these three scenarios.

- `lambdaisland.integreat.prod` for use in your main namespace
- `lambdaisland.integreat.dev` for use in `user.clj`
- `lambdaisland.integreat.test` for use in tests

Main namespace, here the system will be loaded with `:profile :prod`. A shutdown
hook is registered to stop the system when the process terminates. No reference
to the system is kept.

```clojure
(ns my-app.main
  (:gen-class)
  (:require [lambdaisland.integreat.prod :as igreat-prod]
            [my-app.system :as system]))

(defn -main []
  (igreat-prod/go system/setup))
```

For dev we create a number of helpers in `user.clj`, most of which delegate to
`integreat.dev`. These provide integration with `clojure.tools.namespace` to
support code reloading.

```clojure
(ns user)

(defmacro jit [sym]
  `(requiring-resolve '~sym))

(defn setup []
  @(jit co.gaiwan.repohack.system/setup))

(defn browse []
  ((jit clojure.java.browse/browse-url)
   (str "http://localhost:"
        ((jit lambdaisland.integreat.dev/setting) (setup) :http-port))))

(defn go []
  ((jit lambdaisland.integreat.dev/go) (setup)))

(defn reset [] ((jit lambdaisland.integreat.dev/reset)))
(defn reset-all [] ((jit lambdaisland.integreat.dev/reset-all)))
(defn clear [] ((jit lambdaisland.integreat.dev/clear)))
(defn halt [] ((jit lambdaisland.integreat.dev/halt)))
(defn suspend [] ((jit lambdaisland.integreat.dev/suspend)))
(defn resume [] ((jit lambdaisland.integreat.dev/resume)))

(defn ig-config [] @(jit lambdaisland.integreat.dev.state/config))
(defn ig-system [] @(jit lambdaisland.integreat.dev.state/system))
```

For tests the main thing we provide is a function which creates a fixture
function. This will spin up a system with the `:test` profile, bind it to
`integreat.test/*system*`, and tear it down again once the test finishes.

You can provide keys to start, in case your test only relies on part of the
system.

```clojure
(ns app-test
  (:require
   [app.system :as system]
   [clojure.test :refer :all]
   [lambdaisland.integreat.test :as igreat-test]))

(use-fixtures :once (igreat-test/wrap-system
                     system/setup
                     {:keys [:app.persistance/storage-service]}))

(deftest some-test
  ,,,)
  
(comment
  ;; You can use these while working on tests in the REPL
  (igreat-test/init! system/setup)
  (igreat-test/halt!)
  )
```

You can create a namespace with test helpers that provide convenience access to
your system components, e.g. to query the database.

```clojure
(ns test-helpers
  (:require
    [lambdaisland.integreat.test :as igreat-test]
    [some.database :as db))
  
(defn q [qry]
  (db/q (:app.database/conn igreat-test/*system*) qry))
```

## License

Copyright &copy; 2022 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.

