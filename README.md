# clj-Performance-Logger 
[![Build Status](https://travis-ci.org/haduart/clj-performance-logger.svg)](https://travis-ci.org/haduart/clj-performance-logger) [![Dependency Status](https://www.versioneye.com/user/projects/555af96a634daacd41000182/badge.svg?style=flat)](https://www.versioneye.com/user/projects/555af96a634daacd41000182) [![Coverage Status](https://coveralls.io/repos/haduart/clj-performance-logger/badge.svg?branch=master)](https://coveralls.io/r/haduart/clj-performance-logger?branch=master) 

### Performance Logger Made Easy!
[1.1]: http://i.imgur.com/tXSoThF.png (twitter icon with padding)
[2]: https://twitter.com/haduart
clj-performance-logger is a [clojure](http://clojure.org) library that helps you out in logging performance metrics
 in production in [Apache CouchDB](http://couchdb.apache.org/).

## Current Version

[![Clojars Project](http://clojars.org/clj-performance-logger/latest-version.svg)](http://clojars.org/clj-performance-logger)

With Maven:

```bash
<dependency>
  <groupId>clj-performance-logger</groupId>
  <artifactId>clj-performance-logger</artifactId>
  <version>0.1.4</version>
</dependency>
```

With Gradle:
```bash
compile "clj-performance-logger:clj-performance-logger:0.1.4"
```

## Usage

In the `project.clj` add the dependency to clj-performance-logger with the latest stable version:

```clojure
(defproject hps "0.6.2-SNAPSHOT"
  :description "D square High Performance Search"
  :url "http://git.dsquare.intra/projects/HPS/"
  :license "Copyright D square N.V."
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.2"]
                 [cheshire "5.3.1"]
                 ...
                 [clj-performance-logger "0.1.4"]                                  
                 [simpledb "0.1.13"]]

```

In your namespace you need to require it:

```clojure
(ns hps.handler  
  (:require [be.dsquare.logging :as log]))
```

And then initialize it and it's also a good practice ot destroy it (this will change as a component, following a nice start/stop flow).
This will create the default database in case it's not created. 

```clojure
(defn init []  
  (log/init))

(defn destroy []  
  (log/destroy))
```

### timelog  

timelog is followed by a text that will be displayed in the log and will surround the function that we want to check the performance on:
```clojure
(log/timelog "doing simple maths" (* 5 2))
```

This will log the following message inside CouchDB:
```clojure
{:ts "2015-05-15T14:21:00.000Z"
:message "doing simple maths"
:timeMillis 0.5
:namespace "be.dsquare.handler"
:function "clojure.core/*"
:remove "remove cache"}
```
### trace-log

This macro adds extra information (entries) to the same ongoing loging from timelog or timelog-complete. 
```clojure
(defn sum-fn [first-value second-value]
  (do
     (log/trace-log {:first-value   first-value
                     :second-value  second-value})        
     (* first-value second-value)))

(log/timelog "doing simple maths" (sum-fn 5 2))
```

This will log the following message inside CouchDB:
```clojure
{:ts "2015-05-15T14:21:00.000Z"
:message "doing simple maths"
:timeMillis 0.5
:namespace "be.dsquare.handler"
:function "sum-fn"
:first-value  5
:second-value 2
:remove "remove cache"}
```

### log-error-return
 
This macro surrounds a function with a try catch that logs the stacktrace, the function that caused, 
the error message and the customized message that you passed into CouchDB. 

### log-error-http

It's an specific version of the log-error-return. In this case it will return and log and http error 500. 

### Full example

In this example you can see a full complete example on how to use the three main macros: timelog / log-error-http and trace-log
```clojure
(GET "/api/annotations/" [tagNames startDate endDate stateTypes stateNames :as request]
    (->>
      (do
        (log/trace-log {:tagNames   tagNames
                        :startDate  startDate
                        :endDate    endDate})        
        tagNames)
      (map #(invoke-one-annotation % startDate endDate))
      flatten
      (mapv #(assoc-annotation-url request %))
      json-response
      (log/timelog "GET /api/annotations/")
      log/log-error-http))
```
Pay attention that the log-error-http macro has to surround all the other calls, that's why it has to be the 
last one. 

## Contributors

* **(Author)** [Eduard Cespedes Borras](https://github.com/haduart) [![alt text][1.1]][2]
* [Roberto Barchino Garrido](https://github.com/fisoide)
* [Igor Ruzanov](https://github.com/r00z)
* [Jeroen Hoekx](https://github.com/jhoekx)

## Sponsored
This project is sponsored by [D square N.V](http://dsquare.be)

## License

BSD.  See the LICENSE file at the root of this repository.
