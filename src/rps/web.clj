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
            [rps.application :as app]
            [rps.messages :as m]))

(defn new-uuid [] (.toString (java.util.UUID/randomUUID)))

(defn log 
  ([level message]
    (app/publish-message @app/application "service" (m/log-event level message)))
  ([level message details]
    (app/publish-message @app/application "service" (assoc (m/log-event level message) :details details))))

(defn render-move-form 
  ([game-id]
    (let [uri (str "/games/" game-id)]
      [:form {:action uri :method "post"} 
                [:select {:name "move"}
                 [:option {:value "rock"} "Rock"]
                 [:option {:value "paper"} "Paper"]
                 [:option {:value "scissors"} "Scissors"]]
                [:input {:type "submit" :value "Make move"}]])))

(defn render-moves [moves]
  [:ul (map (fn [{:keys [player move]}] [:li (str (name player) " moved " move)]) moves)])

(defn render-header [player]
  [:div
   (if player
      [:form {:action "/logout" :method "GET"}
       [:p "Current user: " player
        [:input {:type "submit" :value "Logout"}]]]
     [:form {:action "/login" :method "POST"}
      [:input {:name "identifier" :type "hidden" :value "https://www.google.com/accounts/o8/id"}]
      [:input {:type "submit" :value "Login using Google"}]])
   [:hr]])

(defn render-game [game-id player]
  (let [game (app/load-aggregate @app/application game-id)
        players (:players game)
        playing? (some #{player} players)
        moved? (some #(= player (:player %)) (:moves game))]
    (if-not game
      (ring.util.response/not-found
        (html [:body
               (render-header player)
               [:h2 "Not found"]]))
      (html [:body
             (render-header player)
               [:div 
                [:h2 (str "[" (first players) "] vs [" (second players) "]")]
                [:p (str "Created by " (:creator game))]
                (condp = (:state game)
                  "started" (if (and playing? (not moved?))
                              (render-move-form game-id)
                              [:p "Waiting..."])
                  "completed" [:div
                               (if (= "won" (:result game))
                                 [:p "Winner is: " (:winner game)]
                                 [:p "Tie!"])
                               (render-moves (:moves game))]
                  "???")]]))))

(defn get-player-id 
  [request]
  (if-let [auth (friend/current-authentication request)]
    (:email auth)
    nil))

(defn ensure-vector [arg]
  (if-not arg
    []
    (if (vector? arg)
      arg
      [arg])))

(defroutes app
  (GET "/" req
       (let [player (get-player-id req)]
         (html 
           [:body
            (render-header player)
            (if player
              [:form {:action "/" :method "POST"} 
               [:label {:for "player"} "Opponent:" [:input {:type "text" :name "player"}]]
               [:input {:type "submit" :value "Create game"}]])])))
  (POST "/" r 
        (friend/authenticated
          (let [game-id (app/new-id)
                creator (get-player-id r)
                player-input (ensure-vector (get-in r [:form-params "player"]))
                players (case (count (filter #(not (.isEmpty (.trim %))) player-input))
                                     1 (conj player-input creator)
                                     2 player-input
                                     (throw (Exception. "Incorrect number of players")))]
            (app/handle-command @app/application (m/->CreateGameCommand game-id creator players))
            (ring.util.response/redirect-after-post (str "/games/" game-id)))))
  (GET "/games/:game-id" [game-id :as request]
       (render-game game-id (get-player-id request)))
  (POST "/games/:game-id" [game-id move :as r]
        (friend/authenticated
          (app/handle-command @app/application (m/->MakeMoveCommand game-id (get-player-id r) move))
          (ring.util.response/redirect-after-post (str "/games/" game-id))))
  (GET "/logout" req
       (log "INFO" (str "User " (get-player-id req) " logged out"))
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

(defn logged-in [credential]
  (log "INFO" (str "User " (:email credential) " logged in"))  
  credential)

(defn wrap-log-exceptions
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable ex
        (println "PRINTLN:" request)
        (println "PRINT-STR: " (print-str request))
        (log "ERROR" 
             (format "Exception in request %s %s: %s"
                     (:request-method request)
                     (:uri request)
                     (.getMessage ex))
             (select-keys request [:remote-addr :query-params :form-params :request-method :content-type :uri :server-name :content-length]))
        (throw ex)))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        store (cookie/cookie-store {:key (env :session-secret)})
        app @app/application]
    (jetty/run-jetty (-> #'app
                         wrap-log-exceptions
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (friend/authenticate
                                {:allow-anon? true
	                                :default-landing-uri "/"
	                                :workflows [(openid/workflow
	                                              :openid-uri "/login"
	                                              :credential-fn logged-in)]})
                         (site {:session {:store store}}))
                     {:port port :join? false})))


