(ns lambdaisland.integreat.dev
  (:require [integrant.core :as ig]
            [lambdaisland.integreat :as igreat]
            [lambdaisland.integreat.dev.state :as state]
            [clojure.tools.namespace.repl :as repl]))

(repl/disable-reload! (find-ns 'integrant.core))

(defn setting
  ([setup k]
   (setting k nil))
  ([setup k aero-opts]
   ((igreat/build-config-provider
     (:settings setup)
     (merge {:profile :dev} aero-opts))
    k)))

(defn secret
  ([setup k]
   (setting k nil))
  ([setup k aero-opts]
   ((igreat/build-config-provider
     (:secrets setup)
     (merge {:profile :dev} aero-opts))
    k)))

(defn prep [setup aero-opts]
  (alter-var-root #'state/setup (constantly setup))
  (alter-var-root #'state/aero-opts (constantly aero-opts))
  (let [config (igreat/read-system-config (if (var? setup)
                                            @setup
                                            setup)
                                          (merge {:profile :dev}
                                                 aero-opts))]

    (alter-var-root #'state/config (constantly config))))

(defn- halt-system [system]
  (when system (ig/halt! system)))

(defn- build-system [build wrap-ex]
  (try
    (build)
    (catch clojure.lang.ExceptionInfo ex
      (if-let [system (:system (ex-data ex))]
        (try
          (ig/halt! system)
          (catch clojure.lang.ExceptionInfo halt-ex
            (throw (wrap-ex ex halt-ex)))))
      (throw ex))))

(defn- init-system [config keys]
  (build-system
   (if keys
     #(ig/init config keys)
     #(ig/init config))
   #(ex-info "Config failed to init; also failed to halt failed system"
             {:init-exception %1}
             %2)))

(defn- resume-system [config system]
  (build-system
   #(ig/resume config system)
   #(ex-info "Config failed to resume; also failed to halt failed system"
             {:resume-exception %1}
             %2)))

(defn init
  ([] (init nil))
  ([keys]
   (alter-var-root #'state/system (fn [sys]
                                    (halt-system sys)
                                    (init-system state/config keys)))
   :initiated))

(defn go
  ([setup] (go setup nil))
  ([setup aero-opts]
   (prep setup aero-opts)
   (init (:keys setup))))

(defn clear []
  (alter-var-root #'state/system (fn [sys] (halt-system sys) nil))
  (alter-var-root #'state/config (constantly nil))
  :cleared)

(defn halt []
  (halt-system state/system)
  (alter-var-root #'state/system (constantly nil))
  :halted)

(defn suspend []
  (when state/system (ig/suspend! state/system))
  :suspended)

(defn resume []
  (if state/setup
    (let [cfg (prep state/setup state/aero-opts)]
      (alter-var-root #'state/config (constantly cfg))
      (alter-var-root #'state/system (fn [sys]
                                       (if sys
                                         (resume-system cfg sys)
                                         (init-system cfg nil))))
      :resumed)
    (throw (Error. "No system setup found."))))

(defn reset []
  (suspend)
  (repl/refresh :after 'lambdaisland.integreat.dev/resume))

(defn reset-all []
  (suspend)
  (repl/refresh-all :after 'lambdaisland.integreat.dev/resume))
