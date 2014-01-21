(ns rps.application
  (:require [rps.messages :as msg]
            [monger.core :as mongo]
            [monger.collection :as mc]
            [rps.core :as c]
            [rps.logic :as logic]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.exchange  :as le]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]
            [cheshire.core :refer :all])
  (:import [org.bson.types ObjectId]))

(def amqp-conn 
  (let [uri (get (System/getenv) "RABBITMQ_URL" "amqp://guest:guest@localhost")]
    (rmq/connect {:uri uri :ssl false})))

(defprotocol ApplicationService
  (handle-command [this command]))

(def connected (java.util.concurrent.atomic.AtomicBoolean.))

(defn connect-if-necessary []
  (if-not (.get connected)
    (do
      (.set connected true)
      (mongo/connect-via-uri! (get (System/getenv) "MONGODB_URL" "mongodb://127.0.0.1/rps")))))

(defn new-id [] (.toString (ObjectId.)))

(defn load-aggregate [id]
  (let [oid (ObjectId. id)]
    (connect-if-necessary)
    (mc/find-map-by-id "aggregates" oid)))

(def exchange-type-topic "topic")

(def singleton
  (let [channel (lch/open amqp-conn)
        publish-message! (fn [routing-key message]
                           (let [json-data (generate-string message)]
                             (println "Publishing:" json-data)
                             (lb/publish channel "lab" routing-key json-data :content-type "application/json")))]
    (le/declare channel "lab" exchange-type-topic :durable true :auto-delete false)
    (publish-message! "service" (msg/service-online-event))
    (reify ApplicationService
      (handle-command [this command]
        (connect-if-necessary)
        (let [oid (ObjectId. (:aggregate-id command))
              current-state (mc/find-map-by-id "aggregates" oid)
              new-events (c/perform command current-state)
              new-state (c/apply-events current-state new-events)]
          (doseq [event new-events] (publish-message! "game" event))
          (mc/update-by-id "aggregates" oid (assoc new-state "_id" oid) :upsert true))))))

