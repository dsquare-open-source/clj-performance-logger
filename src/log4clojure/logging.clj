(ns log4clojure.logging
  (:require [couchdb-extension.couchdb :as couchdb]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [ch.qos.logback.classic Level Logger]
           [org.slf4j LoggerFactory MDC]
           [java.io StringWriter]
           [org.joda.time DateTime]))

(def databaseName "hps-logging")

(def errorDatabaseName "hps-error-logging")

(def ^{:doc "Taken from the demo project
  https://github.com/vaughnd/clojure-example-logback-integration/"}
  logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "hps"))

(defmacro debug [& msg]
  `(.debug logger (print-str ~@msg)))

(defmacro info [& msg]
  `(.info logger (print-str ~@msg)))

(defmacro error [throwable & msg]
  `(if (instance? Throwable ~throwable)
     (.error logger (print-str ~@msg) ~throwable)
     (.error logger (print-str ~throwable ~@msg))))

(defmacro spy
  "Taken from the demo project
  https://github.com/vaughnd/clojure-example-logback-integration/"
  [expr]
  `(let [a# ~expr
         w# (StringWriter.)]
     (pprint/pprint '~expr w#)
     (.append w# " => ")
     (pprint/pprint a# w#)
     (error (.toString w#))
     a#))

(defn spit-content
  "fileVector: is an array with the name of the file and the parameters.
  f: is the function that you are wrapping around.
  map: the values that are gonna be stored in the file.
  (spit-content [\"get-tag-details-\" tagName] walk/keywordize-keys (historian/get-tag-details tagName))"
  [fileVector f map]
  (do
    (spit (reduce #(str %1 "#" %2) fileVector) map)
    (f map)))

(defn slurp-content
  [fileVector]
  (->>
    fileVector
    (reduce #(str %1 "#" %2))
    io/resource
    slurp
    json/read-str))

(def log-agent (agent {:name "name"}))

(def error-log-agent (agent nil))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn- persist-log-entry [entry]
  (couchdb/update-value databaseName (uuid) entry))

(defn- persist-error-log-entry [entry]
  (couchdb/update-value errorDatabaseName (uuid) entry))

(defn update-log-map [originalMap {dynamic :dynamic-token fix :fix-token remove :remove :as newValue}]
  (let [bigKeyword (str fix ":" dynamic)
        assocMap (assoc originalMap bigKeyword (merge (get originalMap bigKeyword) newValue))]
    (if (nil? remove)
      assocMap
      (do
        (->
          assocMap
          (get bigKeyword)
          (dissoc :remove )
          (persist-log-entry))
        (dissoc originalMap bigKeyword)))))

(defn update-map [old newMap] newMap)

(def ^:dynamic *logPrinter* nil)

(def serverup? (atom false))
(def activeLogsMap (atom {}))

(defn update-watch-agent [key reference old-state new-state]
  (do
    (when @serverup?
      (swap! activeLogsMap update-log-map new-state))
    (info (str new-state))))

(defn write-error-logging [key reference old-state new-state]
  (do
    (when @serverup?
      (persist-error-log-entry new-state))
    (error (str new-state))))

(defn init []
  (do
    (when (couchdb/server-is-up? databaseName)
      (do
        (couchdb/create-db databaseName)
        (couchdb/create-db errorDatabaseName)
        (swap! serverup? (fn [_ x] x) true)))
    (add-watch log-agent :logging update-watch-agent)
    (add-watch error-log-agent :logging write-error-logging)))

(defn destroy []
  (do
    (remove-watch log-agent :logging )
    (remove-watch error-log-agent :logging )))

(defn trace-log [msg]
  (send log4clojure.logging/log-agent log4clojure.logging/update-map
    (merge log4clojure.logging/*logPrinter* msg)))

(defmacro timelog [message [function & args :as all] & functions]
  `(binding [log4clojure.logging/*logPrinter* {:fix-token (hash '~&form) :dynamic-token (rand-int 10000)}]
     (let [start# (. System (nanoTime))
           ret# ~all]
       (send log4clojure.logging/log-agent log4clojure.logging/update-map
         (merge {:ts (str (DateTime.))
                 :message '~message
                 :timeMillis (/ (double (- (. System (nanoTime)) start#)) 1000000.0)
                 :namespace ~(str *ns*)
                 :function '~function
                 :remove "remove cache"}
           log4clojure.logging/*logPrinter*))
       ret#)))

(defmacro timelog-complete [message [function & args :as all] & functions]
  `(binding [log4clojure.logging/*logPrinter* {:fix-token (hash '~&form) :dynamic-token (rand-int 10000)}]
     (let [start# (. System (nanoTime))
           ret# ~all]
       (send log4clojure.logging/log-agent log4clojure.logging/update-map
         (merge {:ts (str (DateTime.))
                 :message '~message
                 :timeMillis (/ (double (- (. System (nanoTime)) start#)) 1000000.0)
                 :namespace ~(str *ns*)
                 :function '~function
                 :args '~args
                 :result ret#
                 :form '~&form
                 :env '~(str &env)
                 :remove "remove cache"}
           log4clojure.logging/*logPrinter*))
       ret#)))

(defmacro log-error [exception-type [function & args :as all] & functions]
  `(try
     ~all
     (catch ~exception-type e#
       (send log4clojure.logging/error-log-agent log4clojure.logging/update-map
         {:ts (str (DateTime.))
          :namespace ~(str *ns*)
          :functionName (:name (meta #'~function))
          :file (:file (meta #'~function))
          :column (:column (meta #'~function))
          :line (:line (meta #'~function))
          :arglists (:arglists (meta #'~function))
          :env '~(str &env)
          :cause (.getCause e#)
          :errorMessage (.getMessage e#)
          :stackTrace (.toString e#)
          :statTraceList (mapv #(str %) (.getStackTrace e#))}))))

(defmacro log-error-return [return-message [function & args :as all] & functions]
  `(try
     ~all
     (catch Exception e# (do
                           (send log4clojure.logging/error-log-agent log4clojure.logging/update-map
                             {:ts (str (DateTime.))
                              :namespace ~(str *ns*)
                              :functionName (:name (meta #'~function))
                              :file (:file (meta #'~function))
                              :column (:column (meta #'~function))
                              :line (:line (meta #'~function))
                              :arglists (:arglists (meta #'~function))
                              :env '~(str &env)
                              :cause (.getCause e#)
                              :errorMessage (.getMessage e#)
                              :stackTrace (.toString e#)
                              :statTraceList (mapv #(str %) (.getStackTrace e#))})
                           (when-not (nil? log4clojure.logging/*logPrinter*)
                             (send log4clojure.logging/log-agent log4clojure.logging/update-map
                               (merge {:ts (str (DateTime.))
                                       :message "Exception!"
                                       :exception true
                                       :namespace ~(str *ns*)
                                       :remove "remove cache"}
                                 log4clojure.logging/*logPrinter*)))
                           (if (nil? (ex-data e#))
                             '~return-message
                             (ex-data e#))))))

(defmacro log-error-http [[function & args :as all] & functions]
  `(log-error-return {:status 500
                      :headers {"Content-Type" "application/json"}
                      :body (json/write-str "There's been an Exception")}
     ~all))

