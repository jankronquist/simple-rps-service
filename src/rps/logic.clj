(ns rps.logic
  (:require [rps.framework :as f]
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

(defmethod f/apply-event "GameCreatedEvent" [state event]
  (let [creator (get-in event [:body :createdBy])
        players (get-in event [:body :players])]
    (assoc state
      :state :started
      :creator creator
      :players players)))

(defmethod f/apply-event "MoveMadeEvent" [state event]
  (let [player (get-in event [:body :player])
        move (get-in event [:body :move])]
    (assoc state
      :other-player player
      :other-move move)))

(defmethod f/apply-event "GameEndedEvent" [state event]
  (assoc state
    :state :completed))

; game aggregate command handler

(extend-protocol f/CommandHandler
  rps.messages.CreateGameCommand
  (perform [{:keys [aggregate-id creator players]} state]
    (when (:state state)
      (throw (Exception. "Game already exists")))
    [(m/game-created-event aggregate-id creator players)])

  rps.messages.MakeMoveCommand
  (perform [{:keys [aggregate-id player move]} {:keys [state players other-player other-move]}]
    (when-not (= state :started)
      (throw (Exception. "Incorrect state")))
    (when-not (contains? players player)
      (throw (Exception. "Player not playing this game")))
    (when-not (= other-player player)
      (throw (Exception. "Player has already made a move")))
    (let [events [(m/move-made-event aggregate-id player move)]]
      (if-not other-move
        events
        (conj events 
              (case (compare-moves other-move move)
                :victory (m/game-ended-event aggregate-id {other-player 1 player 0})
                :loss (m/game-ended-event aggregate-id {player 1 other-player 0})
                :tie (m/game-ended-event aggregate-id {player 0 other-player 0})))))))
