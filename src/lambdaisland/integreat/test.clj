(ns lambdaisland.integreat.test
  (:require [integrant.core :as ig]
            [lambdaisland.integreat :as igreat]))

(def config nil)
(def system nil)
(def setup nil)
(def aero-opts nil)

(defn go
  ([setup]
   (go setup nil))
  ([setup aero-opts]
   (let [config  (igreat/read-system-config setup (merge {:profile :prod} aero-opts))
         system  (ig/init config (:keys setup (keys config)))])))


(let [overrides])
(with-redefs [ig/init-key (fn [k conf]
                            )])
