(ns rps.messages
  (:require [monger.core :as mongo]
            [environ.core :refer [env]]))

(def entryPoint "http://simple-rps-service.herokuapp.com")

; events

(defn make-message [stream-id type body]
  {:type type
   :body body
   :meta {}
   :streamId stream-id
   :createdAt (System/currentTimeMillis)
   :messageId (.toString (java.util.UUID/randomUUID))})

(defn game-created-event [game-id created-by players]
  (make-message 
    game-id 
    "GameCreatedEvent"
    {:createdBy created-by
     :players players
     :gameType "rock-paper-scissors"
     :gameUrl (str entryPoint "/games/" game-id)}))

(defn game-won-event 
  [game-id scores winner loser]
  (make-message 
    game-id 
    "GameEndedEvent"
    {:result "won"
     :scores scores
     :winner winner
     :loser loser
     :gameType "rock-paper-scissors"}))

(defn game-tied-event 
  [game-id scores]
  (make-message 
    game-id 
    "GameEndedEvent"
    {:result "tied"
     :scores scores
     :gameType "rock-paper-scissors"}))

(defn move-made-event [game-id player move]
  (make-message 
    game-id 
    "MoveMadeEvent"
    {:player player
     :move move}))

(defn service-online-event []
  (make-message 
    "rock-paper-scissors" 
    "ServiceOnlineEvent"
    {:description "Game engine for Rock Paper Scissors"
     :serviceUrl entryPoint
     :sourceUrl "https://github.com/jankronquist/simple-rps-service"
     :createdBy "Jan Kronquist"}))

(defn log-event [level context message]
  (make-message 
    "rock-paper-scissors" 
    "LogEvent"
    {:level level
     :context context
     :message message}))

; commands

(defrecord CreateGameCommand [aggregate-id creator players])
(defrecord MakeMoveCommand [aggregate-id player move])
