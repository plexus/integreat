(ns lambdaisland.integreat.dotenv
  "Parsing of dotenv files"
  (:require [clojure.string :as str]))

(defn split-at-pred [pred coll]
  (loop [[y & ys] coll
         xs []]
    (cond
      (pred y)
      [(conj xs y) ys]
      (nil? ys)
      nil
      :else
      (recur ys (conj xs y)))))

(defn unquote-chars [s]
  (str/replace s #"\\(u[0-9a-fA-F]{4,}|.)"
               (fn [[_ m]]
                 (case m
                   "r" "\r"
                   "n" "\n"
                   "t" "\t"
                   "f" "\f"
                   "b" "\b"
                   (if (= \u (first m))
                     (str (char (Long/parseLong (subs m 1) 16)))
                     m)))))

(defn expand-vars [s vars]
  (str/replace s #"\$\{([a-zA-Z_]+[a-zA-Z0-9_]*)\}"
               (fn [[_ k]]
                 (str
                  (or (get vars k)
                      (System/getenv k)
                      (throw (ex-info "Failed to expand variable reference" {:value s
                                                                             :unfound-var k})))))))

(defn parse-dotenv [contents {:keys [expand?]}]
  (let [lines (str/split contents #"\R")]
    (loop [vars {}
           [line & lines] lines]
      (cond
        (nil? line)
        vars
        (re-find #"^\s*(#.*)?$" line)
        (recur vars lines)
        :else
        (if-let [[_ _ k v] (re-find #"^\s*(export\s+)?([a-zA-Z_]+[a-zA-Z0-9_]*)\s*=\s*(.*)" line)]
          (case (first v)
            \'
            (let [v (subs v 1)
                  [ql rl :as split] (split-at-pred #(re-find #"(^|[^\\])'" %)
                                                   (cons v lines))]
              (when-not split
                (throw (ex-info "Unterminated quoted value" {:key k})))
              (if-let [[_ ll] (re-find #"(.*)'\s*(#.*)?$" (last ql))]
                (recur (conj vars {k (str/join "\n" (concat (butlast ql) [ll]))})
                       rl)
                (throw (ex-info "Invalid single quoted value" {:key k}))))

            \"
            (let [v (subs v 1)
                  [ql rl :as split] (split-at-pred #(re-find #"(^|[^\\])\"" %)
                                                   (cons v lines))]
              (when-not split
                (throw (ex-info "Unterminated quoted value" {:key k})))
              (if-let [[_ ll] (re-find #"(.*)\"\s*(#.*)?$" (last ql))]
                (recur (assoc vars k (cond-> (unquote-chars (str/join "\n" (concat (butlast ql) [ll])))
                                       expand?
                                       (expand-vars vars)))
                       rl)
                (throw (ex-info "Invalid double quoted value" {:key k}))))

            (recur (assoc vars k (cond-> (unquote-chars (str/replace v #"\s*#.*" ""))
                                   expand?
                                   (expand-vars vars))) lines))
          (throw (ex-info "Invalid syntax" {:line line})))))))
