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

(def exchange-type-topic "topic")

(defprotocol ApplicationService
  (handle-command [this command])
  (load-aggregate [this id])
  (publish-message [this routing-key message]))

(defn new-id [] (.toString (java.util.UUID/randomUUID)))

(def application (delay
  (let [rabbitmq-uri (get (System/getenv) "RABBITMQ_URL" "amqp://guest:guest@localhost")
        amqp-conn (rmq/connect {:uri rabbitmq-uri :ssl false})
        channel (lch/open amqp-conn)
        result (reify ApplicationService
                 (publish-message [this routing-key message]
                   (let [json-data (generate-string message)]
                     (apply lb/publish channel "lab" routing-key json-data 
                                 :content-type "application/json"
                                 (flatten (seq (meta message))))))
                 
                 (load-aggregate [this id]
                   (if-let [existing (mc/find-one-as-map "aggregates" {:aggregateId id})]
                     existing
                     {:aggregateId id
                      :version 0}))

                 (handle-command [this command]
                   (let [id (:aggregate-id command)
                         current-state (load-aggregate this id)
                         current-version (:version current-state)
                         new-events (c/perform command current-state)
                         new-state (c/apply-events current-state new-events)
                         result (assoc new-state :version (inc current-version))]
                     (if-not (:_id current-state)
                       (mc/insert "aggregates" result)
                       (let [write-result (mc/update 
                                            "aggregates" 
                                            {:aggregateId id :version current-version} 
                                            result)]
                         (if (== 0 (.getN write-result))
                           (let [message (str "Failed to perform command " (print-str command))]
                             (publish-message this "service" (msg/log-event "WARN" message))
                             (throw (new java.util.ConcurrentModificationException message))))))
                     (doseq [event new-events] (publish-message this "game" event))
                     new-events)))]
    (mongo/connect-via-uri! (get (System/getenv) "MONGODB_URL" "mongodb://127.0.0.1/rps"))
    (le/declare channel "lab" exchange-type-topic :durable true :auto-delete false)
    (publish-message result "service" (msg/service-online-event))
    result)))
