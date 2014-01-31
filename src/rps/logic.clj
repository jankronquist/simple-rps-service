(ns rps.logic
  (:require [rps.core :as c]
            [rps.messages :as m]))

; move rules

(defmulti compare-moves vector)
(defmethod compare-moves ["rock" "rock"] [x y] :tie)
(defmethod compare-moves ["rock" "paper"] [x y] :loss)
(defmethod compare-moves ["rock" "scissors"] [x y] :victory)
(defmethod compare-moves ["paper" "rock"] [x y] :victory)
(defmethod compare-moves ["paper" "paper"] [x y] :tie)
(defmethod compare-moves ["paper" "scissors"] [x y] :loss)
(defmethod compare-moves ["scissors" "rock"] [x y] :loss)
(defmethod compare-moves ["scissors" "paper"] [x y] :victory)
(defmethod compare-moves ["scissors" "scissors"] [x y] :tie)

; game aggregate - event handlers

(defmethod c/apply-event "GameCreatedEvent" [state event]
  (let [creator (:createdBy event)
        players (:players event)]
    (assoc state
      :state "started"
      :creator creator
      :players players)))

(defmethod c/apply-event "MoveMadeEvent" [state event]
  (let [player (:player event)
        move (:move event)
        moves (:moves state)]
    (assoc state
           :other-player player
           :other-move move
           :moves (conj moves
                        {:player player
                         :move move}))))

(defmethod c/apply-event "GameEndedEvent" [state event]
  (assoc state
    :state "completed"
    :result (:result event)
    :winner (:winner event)
    :loser (:loser event)))

; game aggregate command handler

(extend-protocol c/CommandHandler
  rps.messages.CreateGameCommand
  (perform [{:keys [aggregate-id creator players]} state]
    (when (:state state)
      (throw (Exception. "Game already exists")))
    [(m/game-created-event aggregate-id creator players)])

  rps.messages.MakeMoveCommand
  (perform [{:keys [aggregate-id player move]} {:keys [state players other-player other-move moves]}]
    (when-not (= state "started")
      (throw (Exception. "Incorrect state")))
    (when-not (some #{player} players)
      (throw (Exception. "Player not playing this game")))
    (when (= other-player player)
      (throw (Exception. "Player has already made a move")))
    (let [events [(m/move-made-event aggregate-id player move)]]
      (if-not other-move
        events
        (conj events 
              (case (compare-moves other-move move)
                :victory (m/game-won-event aggregate-id {other-player 1 player 0} other-player player)
                :loss (m/game-won-event aggregate-id {player 1 other-player 0} player other-player)
                :tie (m/game-tied-event aggregate-id {player 0 other-player 0})))))))
