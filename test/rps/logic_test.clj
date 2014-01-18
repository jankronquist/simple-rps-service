(ns rps.logic-test
  (:require [clojure.test :refer :all]
            [rps.logic :refer :all]
            [rps.framework :as f]
            [rps.messages :as m]))

(deftest first-test
  (let [events (f/perform (m/->CreateGameCommand "1" "creator" ["ply1" "ply2"]) {})
        state (f/apply-events {} events)]
  (println state)))
