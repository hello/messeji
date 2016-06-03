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

(defn payload-wrapper
  ^Proxy$PayloadWrapper [^bytes payload ^Proxy$PayloadWrapper$Type type id]
  (.. (Proxy$PayloadWrapper/newBuilder)
    (setType type)
    (setPayload (ByteString/copyFrom payload))
    (setId id)
    build))

(defn error-response
  ^bytes [status-code id]
  (.. (Proxy$PayloadWrapper/newBuilder)
    (setType Proxy$PayloadWrapper$Type/ERROR)
    (setId id)
    (setStatusCode status-code)
    build
    toByteArray))

(defn dispatch-message-async
  [sense-id message]
  (prn message)
  (let [wrapper (Proxy$PayloadWrapper/parseFrom (bs/to-byte-array message))
        payload-id (.getId wrapper)
        url (condp = (.getType wrapper)
              Proxy$PayloadWrapper$Type/RECEIVE_MESSAGES messeji-localhost
              ;; TODO the other endpoints lol
              Proxy$PayloadWrapper$Type/BATCH "batch-url"
              ;; And logs etc...
              )]
    (prn payload-id)
    (->
      (d/let-flow [response (http/post
                              url
                              {:body (.toByteArray (.getPayload wrapper))
                               :headers {"X-Hello-Sense-Id" sense-id}})]
        (prn response)
        (-> response
          :body
          bs/to-byte-array
          (payload-wrapper (.getType wrapper) payload-id)
          .toByteArray))
      (d/catch clojure.lang.ExceptionInfo
        (fn [e]
          (if-let [status-code (-> e .getData :status)]
            (error-response status-code payload-id)
            (error-response 500 payload-id))))
      (d/catch Exception
        (fn [e]
          (error-response 500))))))

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

(defn echo-handler
  "This is another asynchronous handler, but uses `let-flow` instead of `chain` to define the
   handler in a way that at least somewhat resembles the synchronous handler."
  [req]
  (->
    (d/let-flow [socket (http/websocket-connection req)]
      (s/connect socket socket))
    (d/catch
      (fn [_]
        non-websocket-request))))

(def handler
  (compojure/routes
    (GET "/dispatch" req (dispatch-handler req))
    (GET "/echo" [] echo-handler)
    (route/not-found "No such page.")))

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

(defrecord Client [conn last-id]

  java.io.Closeable
  (close [_]
    (.close conn)))

(defn client
  [sense-id]
  (map->Client
    {:conn @(http/websocket-client "ws://localhost:11000/dispatch" {:headers {"X-Hello-Sense-Id" sense-id}})
     :last-id (atom 0)}))

(defn signed-request
  [sense-id key acked-message-ids id]
  (let [request-proto (pb/receive-message-request
                        {:sense-id sense-id
                         :message-read-ids acked-message-ids})
        signed-proto (client/sign-protobuf request-proto key)
        wrapped (payload-wrapper signed-proto Proxy$PayloadWrapper$Type/RECEIVE_MESSAGES id)]
    (.toByteArray wrapped)))

(defn parse-payload-wrapper
  [^Proxy$PayloadWrapper payload-wrapper]
  (condp = (.getType payload-wrapper)

    Proxy$PayloadWrapper$Type/ERROR
      (.getStatusCode payload-wrapper)

    Proxy$PayloadWrapper$Type/RECEIVE_MESSAGES
      (->> payload-wrapper
        .getPayload
        .toByteArray
        (drop (+ 16 32)) ;; drop injection vector and sig
        byte-array
        pb/batch-message)))

(defn next-message
  [conn]
  (d/chain
    (s/take! conn)
    (fn [response]
      (->> response
        bs/to-byte-array
        Proxy$PayloadWrapper/parseFrom
        parse-payload-wrapper))))

(defn receive-messages
  [{:keys [conn last-id]} sense-id key acked-message-ids]
  (let [request (signed-request sense-id key acked-message-ids (swap! last-id inc))]
    (s/put! conn request)
    (next-message conn)))
