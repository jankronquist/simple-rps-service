(ns rps.framework
  (:require [monger.core :as mongo]
            [monger.collection :as mc]
            [rps.core :as c]
            [rps.logic :as logic])
  (:import [org.bson.types ObjectId]))

(def connected (java.util.concurrent.atomic.AtomicBoolean.))

(defn connect-if-necessary []
  (if-not (.get connected)
    (do
      (.set connected true)
      (mongo/connect-via-uri! (System/getenv "MONGODB_URL")))))

(defn new-id [] (.toString (ObjectId.)))

(defn load-aggregate [id]
  (let [oid (ObjectId. id)]
    (connect-if-necessary)
    (mc/find-map-by-id "aggregates" oid)))

(defn handle-command [command]
  (connect-if-necessary)
  
  (let [oid (ObjectId. (:aggregate-id command))
        current-state (mc/find-map-by-id "aggregates" oid)
        new-events (c/perform command current-state)
        new-state (c/apply-events current-state new-events)]
    (println "oid=" oid)
    (println "events:" new-events)
    (println "new-state=" new-state)
    (mc/update-by-id "aggregates" oid (assoc new-state "_id" oid) :upsert true)))
