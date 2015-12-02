(ns be.dsquare.logging
  (:require [couchdb-extension.couchdb :as couchdb]
            [clojure.core.async :refer [chan <! >! go close! >!! <!!] :as async]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [ch.qos.logback.classic Level Logger]
           [org.slf4j LoggerFactory MDC]
           [java.io StringWriter]
           [org.joda.time DateTime]))

(def databaseName "hps-logging")

(def errorDatabaseName "hps-error-logging")

(def ^:dynamic *logPrinter* nil)

(def serverup? (atom false))

(def activeLogsMap (atom {}))

(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "hps"))

(defn debug [& msg]
  (.debug logger (print-str msg)))

(defn info [& msg]
  (.info logger (print-str msg)))

(defn error [throwable & msg]
  (if (instance? Throwable throwable)
    (.error logger (print-str msg) throwable)
    (.error logger (print-str throwable msg))))

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
          (dissoc :remove)
          (persist-log-entry))
        (dissoc originalMap bigKeyword)))))

(defn update-logs [new-state]
  (do
    (when @serverup?
      (swap! activeLogsMap update-log-map new-state))
    (info (str new-state))))

(defn update-error-logging [new-state]
  (do
    (when @serverup?
      (persist-error-log-entry new-state))
    (error (str new-state))))

(defn init []
  (when (couchdb/server-is-up? databaseName)
    (couchdb/create-db databaseName)
    (couchdb/create-db errorDatabaseName)
    (swap! serverup? (fn [_ x] x) true)))

(defn destroy [] nil)

(defn trace-log [msg]
  (try
    (let [log-chan (chan 1)]
      (go
        (update-logs (<! log-chan))
        (close! log-chan))
      (>!! log-chan (merge be.dsquare.logging/*logPrinter* msg)))
    (catch Exception e#)))

(defmacro timelog [message [function & args :as all] & functions]
  `(binding [be.dsquare.logging/*logPrinter* {:fix-token (hash '~&form) :dynamic-token (rand-int 10000)}]
     (let [log-chan# (chan 1)]
       (go
         (update-logs (<! log-chan#))
         (close! log-chan#))
       (let [start# (. System (nanoTime))
             ret# ~all]
         (>!! log-chan# (merge {:ts         (str (DateTime.))
                                :message    '~message
                                :timeMillis (/ (double (- (. System (nanoTime)) start#)) 1000000.0)
                                :namespace  ~(str *ns*)
                                :function   '~function
                                :remove     "remove cache"}
                               *logPrinter*))
         ret#))))

(defmacro timelog-complete [message [function & args :as all] & functions]
  `(binding [be.dsquare.logging/*logPrinter* {:fix-token (hash '~&form) :dynamic-token (rand-int 10000)}]
     (let [log-chan# (chan 1)]
       (go
         (update-logs (<! log-chan#))
         (close! log-chan#))
       (let [start# (. System (nanoTime))
             ret# ~all]
         (>!! log-chan# (merge {:ts         (str (DateTime.))
                                :message    '~message
                                :timeMillis (/ (double (- (. System (nanoTime)) start#)) 1000000.0)
                                :namespace  ~(str *ns*)
                                :function   '~function
                                :args       '~args
                                :result     ret#
                                :form       '~&form
                                :env        '~(str &env)
                                :remove     "remove cache"}
                               be.dsquare.logging/*logPrinter*))
         ret#))))


(defmacro log-error [exception-type [function & args :as all] & functions]
  `(try
     ~all
     (catch ~exception-type e#
       (try
         (let [error-chan# (chan 1)]
           (go
             (update-error-logging (<! error-chan#))
             (close! error-chan#))
           (>!! error-chan# {:ts            (str (DateTime.))
                             :namespace     ~(str *ns*)
                             :functionName  (:name (meta #'~function))
                             :file          (:file (meta #'~function))
                             :column        (:column (meta #'~function))
                             :line          (:line (meta #'~function))
                             :arglists      (:arglists (meta #'~function))
                             :env           '~(str &env)
                             :cause         (.getCause e#)
                             :errorMessage  (.getMessage e#)
                             :stackTrace    (.toString e#)
                             :statTraceList (mapv #(str %) (.getStackTrace e#))}))
         (catch Exception e#)))))

(defmacro log-error-return [return-message [function & args :as all] & functions]
  `(try
     ~all
     (catch Exception e#
       (let [error-channel# (chan 1)
             log-channel# (chan 1)]
         (go
           (update-error-logging (<! error-channel#))
           (update-logs (<! log-channel#))
           (close! error-channel#)
           (close! log-channel#))
         (>!! error-channel#
              {:ts            (str (DateTime.))
               :namespace     ~(str *ns*)
               :functionName  (:name (meta #'~function))
               :file          (:file (meta #'~function))
               :column        (:column (meta #'~function))
               :line          (:line (meta #'~function))
               :arglists      (:arglists (meta #'~function))
               :env           '~(str &env)
               :cause         (.getCause e#)
               :errorMessage  (.getMessage e#)
               :stackTrace    (.toString e#)
               :statTraceList (mapv #(str %) (.getStackTrace e#))})
         (when-not (nil? be.dsquare.logging/*logPrinter*)
           (>!! log-channel#
                (merge {:ts        (str (DateTime.))
                        :message   "Exception!"
                        :exception true
                        :namespace ~(str *ns*)
                        :remove    "remove cache"}
                       be.dsquare.logging/*logPrinter*)))
         (if (or (nil? (ex-data e#)) (nil? (:status (ex-data e#))))
           '~return-message
           (ex-data e#))))))


(defmacro log-error-http [[function & args :as all] & functions]
  `(log-error-return {:status  500
                      :headers {"Content-Type" "application/json"}
                      :body    "\"There's been an Exception\""}
                     ~all))

