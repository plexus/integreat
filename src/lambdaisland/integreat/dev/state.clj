(ns lambdaisland.integreat.dev.state
  (:require [clojure.tools.namespace.repl :as repl]))

(repl/disable-reload!)

(def config nil)
(def system nil)
(def setup nil)
(def aero-opts nil)
