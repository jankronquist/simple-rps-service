(ns rps.core)

(defmulti apply-event (fn [state event] (:type event)))

(defn apply-events [state events]
  (reduce apply-event state events))

(defprotocol CommandHandler
  (perform [command state]))
