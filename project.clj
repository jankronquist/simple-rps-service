(defproject heroku-rps "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://heroku-rps.herokuapp.com"
  :license {:name "FIXME: choose"
            :url "http://example.com/FIXME"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.2.0"]
                 [compojure "1.1.4"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring/ring-devel "1.1.0"]
                 [environ "0.2.1"]
                 [bultitude "0.1.7"]
                 [org.webjars/foundation "4.0.4"]
                 [hiccup "1.0.4"]]
  :main heroku-rps.web
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]]
  :hooks [environ.leiningen.hooks]
  :profiles {:production {:env {:production true}}})
