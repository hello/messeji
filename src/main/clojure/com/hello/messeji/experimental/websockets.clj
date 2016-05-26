(ns com.hello.messeji.experimental.websockets
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.tools.logging :as log]
    [com.hello.messeji
      [client :as client]
      [handlers :as handlers]
      [protobuf :as pb]]
    [compojure.core :as compojure :refer [GET]]
    [compojure.route :as route]
    [manifold.deferred :as d]
    [manifold.stream :as s])
  (import
    [com.google.protobuf ByteString]
    [com.hello.messeji.api.experimental
      Proxy$PayloadWrapper
      Proxy$PayloadWrapper$Type]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;; Server ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(def messeji-localhost "http://localhost:10000/receive")

;; TODO error handling and pass errors down to client
(defn dispatch-message-async
  [sense-id message]
  (prn message)
  (let [payload-wrapper (Proxy$PayloadWrapper/parseFrom (bs/to-byte-array message))
        url (condp = (.getType payload-wrapper)
              Proxy$PayloadWrapper$Type/RECEIVE_MESSAGES messeji-localhost
              ;; TODO the other endpoints lol
              Proxy$PayloadWrapper$Type/BATCH "batch-url")]
    (d/chain
      (http/post
        url
        {:body (.toByteArray (.getPayload payload-wrapper))
         :headers {"X-Hello-Sense-Id" sense-id}})
      :body)))

(defn dispatch-handler
  [req]
  (let [sense-id (handlers/request-sense-id req)]
    (->
      (d/let-flow [conn (http/websocket-connection req)]
        ;; Take all messages from sense and ship them to the correct place
        (s/consume
          (fn [message]
            (s/connect
              (dispatch-message-async sense-id message)
              conn
              {:downstream? false}))
          conn))
      (d/catch
        (fn [_]
          non-websocket-request)))))

(def handler
  (compojure/routes
    (GET "/dispatch" req (dispatch-handler req))))

(defn start-server
  []
  (http/start-server handler {:port 11000}))

(defn -main
  [& args]
  (let [server (start-server)]
    (prn "server started" server)
    server))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;; Client ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn client
  [sense-id]
  @(http/websocket-client "ws://localhost:11000/dispatch" {:headers {"X-Hello-Sense-Id" sense-id}}))

(defn signed-request
  [sense-id key acked-message-ids]
  (let [request-proto (pb/receive-message-request
                        {:sense-id sense-id
                         :message-read-ids acked-message-ids})
        signed-proto (client/sign-protobuf request-proto key)]
    (.. (Proxy$PayloadWrapper/newBuilder)
      (setType Proxy$PayloadWrapper$Type/RECEIVE_MESSAGES)
      (setPayload (ByteString/copyFrom signed-proto))
      build
      toByteArray)))

(defn next-message
  [client]
  (d/chain
    (s/take! client)
    (fn [response]
      (->> response
        bs/to-byte-array
        (drop (+ 16 32)) ;; drop injection vector and sig
        byte-array
        pb/batch-message))))

(defn receive-messages
  [client sense-id key acked-message-ids]
  (let [request (signed-request sense-id key acked-message-ids)]
    (s/put! client request)
    (next-message client)))
