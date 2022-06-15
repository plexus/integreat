(ns lambdaisland.integreat.prod
  (:require [integrant.core :as ig]
            [lambdaisland.integreat :as igreat]))

(defn go
  ([setup]
   (go setup nil))
  ([setup aero-opts]
   (let [config  (igreat/read-system-config setup (merge {:profile :prod} aero-opts))
         system  (ig/init config (:keys setup (keys config)))
         runtime (java.lang.Runtime/getRuntime)]
     (prn config)
     (prn system)
     (.addShutdownHook
      runtime
      (Thread. (fn []
                 (ig/halt! system)
                 (shutdown-agents))))
     @(promise))))
