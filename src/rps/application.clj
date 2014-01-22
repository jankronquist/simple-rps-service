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

(defprotocol ApplicationService
  (handle-command [this command])
  (load-aggregate [this id]))


(defn new-id [] (.toString (ObjectId.)))

(def exchange-type-topic "topic")

(def singleton (delay
  (let [rabbitmq-uri (get (System/getenv) "RABBITMQ_URL" "amqp://guest:guest@localhost")
        amqp-conn (rmq/connect {:uri rabbitmq-uri :ssl false})
        channel (lch/open amqp-conn)
        publish-message! (fn [routing-key message]
                           (let [json-data (generate-string message)]
                             (println "Publishing:" json-data)
                             (lb/publish channel "lab" routing-key json-data :content-type "application/json")))]
    (mongo/connect-via-uri! (get (System/getenv) "MONGODB_URL" "mongodb://127.0.0.1/rps"))
    (le/declare channel "lab" exchange-type-topic :durable true :auto-delete false)
    (publish-message! "service" (msg/service-online-event))
    (reify ApplicationService
      (load-aggregate [this id]
        (let [oid (ObjectId. id)]
          (mc/find-map-by-id "aggregates" oid)))

      (handle-command [this command]
        (let [oid (ObjectId. (:aggregate-id command))
              current-state (mc/find-map-by-id "aggregates" oid)
              new-events (c/perform command current-state)
              new-state (c/apply-events current-state new-events)]
          (doseq [event new-events] (publish-message! "game" event))
          (mc/update-by-id "aggregates" oid (assoc new-state "_id" oid) :upsert true)))))))
