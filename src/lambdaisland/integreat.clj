(ns lambdaisland.integreat
  (:require [aero.core :as aero]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [integrant.core :as ig]
            [lambdaisland.integreat.dotenv :as dotenv]))

(defrecord Setting [k])
(defrecord Secret [k])

(defmethod aero/reader 'ig/ref [_ _tag value] (ig/ref value))
(defmethod aero/reader 'ig/refset [_ _tag value] (ig/refset value))

(defmethod aero/reader 'setting [_ _ k] (->Setting k))
(defmethod aero/reader 'secret [_ _ k] (->Secret k))

(defn key->env-var [k]
  (if (string? k)
    k
    (str (when (qualified-ident? k)
           (str (str/upper-case (munge (namespace k)))
                "__"))
         (str/upper-case (munge (name k))))))

(comment
  (key->env-var :foo-bar/baz-baq)
  ;; => "FOO_BAR__BAZ_BAQ"
  (key->env-var :baz-baq)
  ;; => "BAZ_BAQ"
  )

(defn env [k]
  (System/getenv (key->env-var k)))

(defn env-read [k]
  (if-let [v (env k)]
    (try
      (edn/read-string v)
      (catch Exception _
        v))))

(defn dotenv
  ([]
   (dotenv ".env" nil))
  ([slurpable]
   (dotenv slurpable nil))
  ([slurpable opts]
   (fn [k]
     (get
      (dotenv/parse-dotenv (slurp slurpable) opts)
      (key->env-var k)))))

(defn build-config-provider [sources aero-opts]
  (if (or (sequential? sources) (nil? sources))
    (reduce
     (fn [acc c]
       (let [lookup (if (or (fn? c) (map? c))
                      c
                      (aero/read-config c aero-opts))]
         (fn [k]
           (if-some [v (lookup k)]
             v
             (acc k)))))
     (fn [k]
       (throw
        (ex-info "Config key not found in any of the sources"
                 {:key k
                  :sources sources
                  :aero-opts aero-opts})))
     (reverse sources))
    (recur [sources] aero-opts)))

(defn read-components [sources aero-opts]
  (if (sequential? sources)
    (reduce
     (fn [acc c]
       (merge-with
        (fn [_ _]
          (throw
           (ex-info "Key provided by multiple configuration sources"
                    (filter (set (keys acc))
                            (keys c)))))
        acc
        (if (map? c)
          c
          (aero/read-config c aero-opts))))
     {}
     sources)
    (recur [sources] aero-opts)))

(defn read-system-config [{:as   setup
                           :keys [settings
                                  secrets
                                  components]}
                          aero-opts]
  (let [setting-provider (build-config-provider settings aero-opts)
        secret-provider  (build-config-provider secrets aero-opts)]
    (doto (walk/postwalk
           (fn [o]
             (cond
               (instance? Setting o)
               (setting-provider (:k o))

               (instance? Secret o)
               (secret-provider (:k o))

               :else o))
           (read-components components aero-opts))
      (ig/load-namespaces))))
