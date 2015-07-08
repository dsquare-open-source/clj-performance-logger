(defproject clj-performance-logger "0.1.5-SNAPSHOT"
  :description "High Performance Logging for Clojure"
  :url "https://github.com/haduart/clj-performance-logger"
  :license {:name "BSD"
            :url "http://www.opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clj-http "1.1.2"]
                 [org.clojure/data.json "0.2.6"]
                 [couchdb-extension "0.1.4"]
                 [com.ashafa/clutch "0.4.0" :exclusions [clj-http]]
                 [ch.qos.logback/logback-classic "1.1.3"]]

  :plugins [[lein-midje "3.1.3"]
            [lein-pprint "1.1.1"]
            [lein-cloverage "1.0.2"]
            [lein-ancient "0.5.5"]]

  :repl-options {:welcome (println "Welcome to the magical world of the repl!")
                 :port 4001}

  :min-lein-version "2.0.0"

  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"] [midje "1.6.3"]
                                  [peridot "0.4.0"]]}}

  :aliases {"dev" ["do" "test"]})
