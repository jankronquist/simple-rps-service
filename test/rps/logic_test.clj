(ns rps.logic-test
  (:use monger.operators)
  (:require [clojure.test :refer :all]
            [rps.logic :refer :all]
            [rps.application :as a]
            [rps.core :as c]
            [rps.messages :as m]
            [monger.collection :as mc])
  (:import [java.util.concurrent Executors]))

(deftest happy-flow
  (let [id (a/new-id)]
    (a/handle-command @a/application (m/->CreateGameCommand id "creator" ["ply1" "ply2"]))
    (a/handle-command @a/application (m/->MakeMoveCommand id "ply1" "rock"))
    (println (a/load-aggregate @a/application id))))

(deftest concurrent-modification
  (let [id (a/new-id)
        c1 (a/handle-command @a/application (m/->CreateGameCommand id "creator" ["ply1" "ply2"]))
        nthreads 4
        pool  (Executors/newFixedThreadPool nthreads)
        tasks (map (fn [t]
                      (fn []
                        (a/handle-command @a/application (m/->MakeMoveCommand id "ply1" "rock"))
                        (a/handle-command @a/application (m/->MakeMoveCommand id "ply2" "paper"))))
                   (range nthreads))
        e (reduce + (map
                  (fn [future]
                    (try
                      (do 
                        (.get future)
                        0)
                      (catch Exception e
   ; TODO check that we get specifically ConcurrentModificationException
                        1)))
                  (.invokeAll pool tasks)))]
      (.shutdown pool)
      (if (== 0 e)
        (throw (new Exception "Expected ConcurrentModificationException")))))
