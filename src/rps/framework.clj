(ns rps.framework
  (:require [monger.core :as mongo]
            [monger.collection :as mc]
            [environ.core :refer [env]])
  (:import [org.bson.types ObjectId]))

(defmulti apply-event (fn [state event] (:type event)))

(defn apply-events [state events]
  (reduce apply-event state events))

(defprotocol CommandHandler
  (perform [command state]))

(mongo/connect-via-uri! (env :mongodb-url))

(defn handle-command [command]
  (let [oid (ObjectId. (:aggregate-id command))
        current-state (mc/find-map-by-id "aggregates" oid)
        new-events (perform command current-state)
        new-state (apply-events current-state new-events)]
    (mc/update-by-id "aggregates" oid new-state)))
