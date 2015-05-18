(ns be.dsquare.logging-test
  (:use clojure.test
        midje.sweet)
  (:require [be.dsquare.logging :as log]
            [couchdb-extension.couchdb :as couchdb]))

(fact "How to merge multiple logs when we already have an entry"
  (let [val-0 {"-259445176:7307" {:pointsFromHistorian 105
                                  :keyTagsFromUi {:tags [{:name "isvag-tag0-hourly-1000-compressed"
                                                          :range 96.1000013, :searchType 1}]
                                                  :timePeriodFilters []
                                                  :originalStartDate "2013-01-04T05:40:46.926Z"
                                                  :originalEndDate "2013-01-07T04:24:28.155Z"}
                                  :fix-token -259445176, :dynamic-token 7307}}
        val-1 {:dynamic-token 7307
               :fix-token -259445176
               :ts "2014-08-08T10:44:34.880+02:00"
               :namespace "hps.handler"
               :function "->"
               :message "POST /api/searchresult/"
               :timeMillis 209.994}
        response {"-259445176:7307" {:pointsFromHistorian 105
                                     :keyTagsFromUi {:tags [{:name "isvag-tag0-hourly-1000-compressed"
                                                             :range 96.1000013, :searchType 1}]
                                                     :timePeriodFilters []
                                                     :originalStartDate "2013-01-04T05:40:46.926Z"
                                                     :originalEndDate "2013-01-07T04:24:28.155Z"}
                                     :ts "2014-08-08T10:44:34.880+02:00"
                                     :namespace "hps.handler"
                                     :function "->"
                                     :message "POST /api/searchresult/"
                                     :timeMillis 209.994
                                     :fix-token -259445176
                                     :dynamic-token 7307}}]
    (log/update-log-map val-0 val-1) => response))

(fact "Check what happens the first time we create the keyword"
  (let [val-1 {:dynamic-token 7307
               :fix-token -259445176
               :ts "2014-08-08T10:44:34.880+02:00"
               :namespace "hps.handler"
               :function "->"
               :message "POST /api/searchresult/"
               :timeMillis 209.994}
        response {"-259445176:7307" {:dynamic-token 7307
                                     :fix-token -259445176
                                     :ts "2014-08-08T10:44:34.880+02:00"
                                     :namespace "hps.handler"
                                     :function "->"
                                     :message "POST /api/searchresult/"
                                     :timeMillis 209.994}
                  "666:999" {:old "value"}}]
    (log/update-log-map {"666:999" {:old "value"}} val-1) => response))

(fact "When we finalize we should assoc the values first, persist the data and remove it from the map"
  (let [val-0 {"-259445176:7307" {:dynamic-token 7307
                                  :fix-token -259445176
                                  :ts "2014-08-08T10:44:34.880+02:00"
                                  :namespace "hps.handler"
                                  :function "->"
                                  :timeMillis 209.994}
               "666:999" {:old "value"}}
        val-1 {:dynamic-token 7307
               :fix-token -259445176
               :message "Do you know that Eduard is the best?"
               :remove "remove"}
        response {"666:999" {:old "value"}}]
    (log/update-log-map val-0 val-1) => response
    (provided (couchdb/update-value "hps-logging" anything
                {:dynamic-token 7307
                 :fix-token -259445176
                 :ts "2014-08-08T10:44:34.880+02:00"
                 :namespace "hps.handler"
                 :function "->"
                 :message "Do you know that Eduard is the best?"
                 :timeMillis 209.994}) => anything)))


;  Until we don't refactor the code the macro is not that small any more....
;  Actual: "(try (str \"Edu\") (catch NullPointerException e__10557__auto__
;                  (clojure.core/send log4clojure/error-log-agent log4clojure/update-map
;                  {:statTraceList (clojure.core/mapv (fn* [p1__10556__10558__auto__]
;                  (clojure.core/str p1__10556__10558__auto__)) (.getStackTrace e__10557__auto__)),
;                  :cause (.getCause e__10557__auto__), :errorMessage (.getMessage e__10557__auto__), :file
;                  (:file (clojure.core/meta (var str))), :env (quote \"\"),
;                  :column (:column (clojure.core/meta (var str))),
;                  :ts (clojure.core/str (org.joda.time.DateTime.)),
;                  :line (:line (clojure.core/meta (var str))),
;                  :arglists (:arglists (clojure.core/meta (var str))),
;                  :namespace \"hps.test.util.log\", :functionName (:name (clojure.core/meta (var str))),
;                  :stackTrace (.toString e__10557__auto__)})))"
;  (fact "How to use log-error macro and what to expect from it"
;    (->
;      (log/log-error NullPointerException (str "Edu"))
;      quote
;      macroexpand
;      str) =>
;    #"try \(str \"Edu\"\) \(catch NullPointerException e__([0-9]+)__auto__ \(log/error e__([0-9]+)__auto__\)")
