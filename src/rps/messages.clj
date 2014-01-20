(ns rps.messages
  (:require [monger.core :as mongo]
            [environ.core :refer [env]]))

; events

(defn make-message [stream-id type body]
  {:type type
   :body body
   :meta {}
   :streamId stream-id
   :createdAt (System/currentTimeMillis)})

(defn game-created-event [game-id created-by players]
  (make-message 
    game-id 
    "GameCreatedEvent"
    {:createdBy created-by
     :players players
     :gameType "rock-paper-scissors"
     :gameUrl (str "http://heroku-rps.herokuapp.com/games/" game-id)}))

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
    {:name "rock-paper-scissors"
     :entryPoint "http://heroku-rps.herokuapp.com"
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
