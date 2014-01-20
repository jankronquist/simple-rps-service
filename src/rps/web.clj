(ns rps.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [cemerick.friend :as friend]
            [cemerick.friend.openid :as openid]
            [cemerick.friend.workflows :as workflows]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [ring.util.response :as resp]
            [hiccup.core :refer [html]]
            [rps.framework :as f]
            [rps.messages :as m]))

(defn new-uuid [] (.toString (java.util.UUID/randomUUID)))

(defn render-move-form 
  ([game-id player]
    (let [uri (str "/games/" game-id "?login=" player)]
      [:form {:action uri :method "post"} 
                [:select {:name "move"}
                 [:option {:value "rock"} "Rock"]
                 [:option {:value "paper"} "Paper"]
                 [:option {:value "scissors"} "Scissors"]]
                [:input {:type "submit" :value "Make move"}]])))

(defn render-moves [moves]
  [:ul (map (fn [[player move]] [:li (str (name player) " moved " move)]) moves)])

(defn render-game [game-id player]
  (let [game (f/load-aggregate game-id)
        playing? (some #{player} (:players game))
        moved? (contains? (:moves game) (keyword player))]
    ; TODO check if game is nil
    (println game)
    (html [:body
           [:p (str "Created by " (:creator game))]
           (condp = (:state game)
             "started" (if (and playing? (not moved?))
                         (render-move-form game-id player)
                         [:p "Waiting..."])
             "completed" [:div
                          (if (= "won" (:result game))
                            [:p "Winner is: " (:winner game)]
                            [:p "Tie!"])
                          (render-moves (:moves game))]
             "???")])))

(defn get-player-id 
  [request]
  (let [auth (friend/current-authentication request)]
    (:email auth)))

(defroutes app
  (GET "/" req
       (html 
         [:body
          (if-let [auth (friend/current-authentication req)]
            [:p "Logged in: " (:identity auth)]
            [:form {:action "/login" :method "POST"}
             [:input {:name "identifier" :type "hidden" :value "https://www.google.com/accounts/o8/id"}]
             [:input {:type "submit" :value "Login"}]])]))
  (POST "/" r 
        (let [game-id (f/new-id)
              players (get-in r [:form-params "player"])]
          ; TODO: players should be a vector of 2 elements
          (f/handle-command (m/->CreateGameCommand game-id (get-player-id r) players))
          (ring.util.response/redirect-after-post (str "/games/" game-id))))
  (GET "/games/:game-id" [game-id :as request]
       (render-game game-id (get-player-id request)))
  (POST "/games/:game-id" [game-id move :as r]
        (f/handle-command (m/->MakeMoveCommand game-id (get-player-id r) move))
        (ring.util.response/redirect-after-post (str "/games/" game-id)))
  (GET "/logout" req
       (friend/logout* (resp/redirect (str (:context req) "/"))))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(def query-param-authentication
  (fn [request]
    (if-let [user (get-in request [:query-params "login"])]
      (do
        (workflows/make-auth {:username user
                              :email user} 
                             {::friend/workflow :http-query-param
                              ::friend/redirect-on-auth? false}))
      nil)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (friend/authenticate
                                {:allow-anon? true
	                                :default-landing-uri "/"
	                                :workflows [query-param-authentication
                                             (openid/workflow
	                                              :openid-uri "/login"
	                                              :credential-fn identity)]})
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
