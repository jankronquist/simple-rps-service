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
  (println (format "[consumer] Received a message: %s, content type: %s, type: %s"
                   (String. payload "UTF-8") content-type type))
  (println "meta=" meta))

(defn -main
  [& args]
  (let [conn  (rmq/connect)
        ch    (lch/open conn)
        qname "langohr.examples.hello-world"]
    (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
    (lq/declare ch qname :exclusive false :auto-delete true)
    (lq/bind ch qname "lab" :routing-key "*") 
    (lc/subscribe ch qname message-handler :auto-ack true)
    (Thread/sleep 3600000)
    (println "[main] Disconnecting...")
    (rmq/close ch)
    (rmq/close conn)))