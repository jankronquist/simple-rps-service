(ns rps.receive
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def ^{:const true}
  default-exchange-name "")

(defn message-handler
  [ch {:keys [content-type delivery-tag type header] :as meta} ^bytes payload]
  (println "Received message!")
  (println "META: " meta)
  (println "BODY: " (String. payload "UTF-8"))
  (println))

(defn -main
  [& args]
  (let [rabbitmq-uri (get (System/getenv) "RABBITMQ_URL" "amqp://guest:guest@localhost")
        conn (rmq/connect {:uri rabbitmq-uri :ssl (.startsWith rabbitmq-uri "amqps")})
        ch    (lch/open conn)
        qname "langohr.examples.hello-world"]
    (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
    (lq/declare ch qname :exclusive false :auto-delete true)
    (lq/bind ch qname "lab" :routing-key "*") 
    (lc/subscribe ch qname (lc/ack-unless-exception message-handler))
    (Thread/sleep 3600000)
    (println "[main] Disconnecting...")
    (rmq/close ch)
    (rmq/close conn)))