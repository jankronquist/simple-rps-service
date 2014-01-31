(ns rps.core)

(defmulti apply-event (fn [state event] (:type (meta event))))

(defn apply-events [state events]
  (reduce apply-event state events))

(defprotocol CommandHandler
  (perform [command state]))
