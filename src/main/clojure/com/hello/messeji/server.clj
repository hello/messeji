(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.db.in-mem :as mem]
    [compojure.core :as compojure :refer [GET POST]]
    [manifold.deferred :as deferred]
    [ring.middleware.params :as params]
    [ring.middleware.content-type :refer [wrap-content-type]])
  (:import
    [com.amazonaws ClientConfiguration]
    [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
    [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
    [com.hello.suripu.core.db KeyStoreDynamoDB]
    [com.hello.suripu.core.util HelloHttpHeader]
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]
    [com.hello.suripu.service SignedMessage]))

#_"
TODO
- protobuf wrapper for
  - unparseable request (400)
  - boilerplate around parsing/serializing protobuf message
  - content type
- assorted line-level code TODOs
- unit and integration tests
"

(defn- batch-message
  [messages]
  (let [builder (Messeji$BatchMessage/newBuilder)]
    (doseq [msg messages]
      (.addMessage builder msg))
    (.build builder)))

(defn- batch-message-response
  [messages]
  {:body (-> messages
          batch-message
          .toByteArray
          bs/to-input-stream)
   :status 200})

(defn- receive-messages
  [connections-atom message-store timeout sense-id]
  (if-let [unacked-messages (seq (db/unacked-messages message-store sense-id))]
    (batch-message-response unacked-messages)
    (let [deferred-response (deferred/deferred)]
      (swap! connections-atom assoc sense-id deferred-response)
      (deferred/timeout!
        deferred-response
        timeout
        (batch-message-response [])))))

(defn- request-sense-id
  [request]
  (-> request :headers (get HelloHttpHeader/SENSE_ID)))

(def ^:private response-400
  {:status 400
   :body ""})

(defn- acked-message-ids
  [^Messeji$ReceiveMessageRequest receive-message-request]
  (seq (.getMessageReadIdList receive-message-request)))

(defn handle-receive
  [connections key-store message-store timeout request]
  (let [sense-id (request-sense-id request)
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (:body request))
        message-ids (acked-message-ids receive-message-request)]
    (if (= sense-id (.getSenseId receive-message-request))
      (do
        (db/acknowledge message-store message-ids)
        (receive-messages connections message-store timeout sense-id))
      response-400)))

(defn- send-messages!
  [connection-deferred messages]
  (when (and connection-deferred
             (not (deferred/realized? connection-deferred)))
    (when-let [delivery (deferred/success!
                          connection-deferred
                          (batch-message-response messages))]
      ; TODO mark sent
      delivery)))

(defn handle-send
  [connections-atom message-store request]
  ;; TODO 400 if no sense id
  (let [sense-id (-> request :headers (get HelloHttpHeader/SENSE_ID))
        message (Messeji$Message/parseFrom (:body request))
        message-with-id (db/create-message message-store sense-id message)]
    (send-messages! (get @connections-atom sense-id) [message-with-id])
    {:status 200
     :body "okay"}))

(defn handler
  [connections key-store message-store timeout]
  (let [routes
        (compojure/routes
          (POST "/receive" request
            (handle-receive connections key-store message-store timeout request))
          (POST "/send" request
            (handle-send connections message-store request)))]
    (-> routes
       wrap-content-type)))

(defn start-server!
  [config-map]
  (let [connections (atom {})
        credentials-provider (DefaultAWSCredentialsProviderChain.)
        client-config (.. (ClientConfiguration.)
                        (withConnectionTimeout 200)
                        (withMaxErrorRetry 1)
                        (withMaxConnections 100))
        ddb-client (doto (AmazonDynamoDBClient. credentials-provider client-config)
                     (.setEndpoint (get-in config-map [:key-store :endpoint])))
        key-store (KeyStoreDynamoDB.
                    ddb-client
                    (get-in config-map [:key-store :table])
                    (.getBytes "1234567891234567") ; TODO remove default key
                    (int 120)) ; 2 minutes for cache
        timeout (get-in config-map [:http :receive-timeout])
        message-store (mem/mk-message-store (:max-message-age-millis config-map))
        server (http/start-server
                 (handler connections key-store message-store timeout)
                 {:port (get-in config-map [:http :port])})]
    {:connections connections
     :server server
     :ddb-client ddb-client}))

(defn -main
  [config-file & args]
  (let [config (-> config-file slurp edn/read-string)
        server (start-server! config)]
    (prn server)
    server))
