(ns heroku-rps.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [cemerick.friend :as friend]
            [cemerick.friend.openid :as openid]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [ring.util.response :as resp]
            [hiccup.core :refer [html]]))

(defroutes app
  (GET "/" req
       (html 
         [:body
          (if-let [auth (friend/current-authentication req)]
            [:p "Logged in! Some information delivered by your OpenID provider:"
             [:ul (for [[k v] auth
                        :let [[k v] (if (= :identity k)
                                      ["Your OpenID identity" (str (subs v 0 (* (count v) 2/3)) "…")]
                                      [k v])]]
                    [:li [:strong (str (name k) ": ")] v])]]
            [:form {:action "/login" :method "POST"}
             [:input {:name "identifier" :type "hidden" :value "https://www.google.com/accounts/o8/id"}]
             [:input {:type "submit" :value "Login"}]])]))
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
                            :workflows [(openid/workflow
                                          :openid-uri "/login"
                                          :credential-fn identity)]})
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
