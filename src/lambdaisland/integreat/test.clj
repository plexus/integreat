(ns lambdaisland.integreat.test
  (:require [integrant.core :as ig]
            [lambdaisland.integreat :as igreat]))

(def ^:dynamic *system* nil)

(defn wrap-system
  "For use as fixture function"
  ([setup]
   (wrap-system setup nil))
  ([setup opts]
   (fn [t]
     (let [ig-config (igreat/read-system-config setup
                                                (merge {:profile :test}
                                                       opts))
           ig-system (ig/init ig-config (:keys opts (:keys setup (keys ig-config))))]
       (binding [*system* ig-system]
         (t))
       (ig/halt! ig-system)))))

(defn init!
  "For use in REPL"
  ([setup]
   (init! setup nil))
  ([setup opts]
   (alter-var-root
    #'*system*
    (fn [sys]
      (if sys
        sys
        (let [ig-config (igreat/read-system-config setup
                                                   (merge {:profile :test}
                                                          opts))]
          (ig/init ig-config (:keys opts (:keys setup (keys ig-config))))))))))

(defn halt!
  "For use in REPL"
  []
  (alter-var-root
   #'*system*
   (fn [sys]
     (when sys
       (ig/halt! sys)))))
