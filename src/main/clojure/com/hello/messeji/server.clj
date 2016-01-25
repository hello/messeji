(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn]
    [com.hello.messeji.db.in-mem :as mem]
    [compojure.core :as compojure :refer [defroutes GET POST]]
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
protobuf wrapper for
  - unparseable request (400)
  - boilerplate around parsing/serializing protobuf message
  - content type
"

;; TODO config
(def max-message-age-millis 10000)

(def ^:private empty-batch-message
  (.build (Messeji$BatchMessage/newBuilder)))

(defn- empty-batch-message-response
  []
  {:status 200
   :body (bs/to-input-stream (.toByteArray empty-batch-message))})

(defn- receive-messages
  [connections-atom timeout sense-id]
  (if-let [unacked-messages (seq (mem/unacked-messages sense-id max-message-age-millis))]
    unacked-messages
    (let [deferred-response (deferred/deferred)]
      (swap! connections-atom assoc sense-id deferred-response)
      (deferred/timeout!
        deferred-response
        timeout
        (empty-batch-message-response)))))

(defn- request-sense-id
  [request]
  (-> request :headers (get HelloHttpHeader/SENSE_ID)))

(def ^:private response-400
  {:status 400
   :body ""})

(defn handle-receive
  [connections key-store timeout request]
  (let [sense-id (request-sense-id request)
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (:body request))]
    ;; TODO ack
    (if (= sense-id (.getSenseId receive-message-request))
      (receive-messages connections timeout sense-id)
      response-400)))

(defn- batch-message
  [messages]
  (let [builder (Messeji$BatchMessage/newBuilder)]
    (doseq [msg messages]
      (.addMessage builder msg))
    (.build builder)))

(defn- send-messages!
  [connection-deferred messages]
  (when (and connection-deferred
             (not (deferred/realized? connection-deferred)))
    (when-let [delivery (deferred/success!
                          connection-deferred
                          (batch-message messages))]
      (mem/mark-sent! messages)
      delivery)))

(defn handle-send
  [connections-atom request]
  (let [sense-id (-> request :headers (get HelloHttpHeader/SENSE_ID))
        message (Messeji$Message/parseFrom (:body request))
        message-with-id (mem/create-message! sense-id message)]
    (send-messages! (get @connections-atom sense-id) [message-with-id])
    {:status 200
     :body "okay"}))

(defn handler
  [connections key-store timeout]
  (let [routes
        (compojure/routes
          (POST "/receive" request
            (handle-receive connections key-store timeout request))
          (POST "/send" request
            (handle-send connections request)))]
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
        server (http/start-server
                 (handler connections key-store timeout)
                 {:port (get-in config-map [:http :port])})]
    (prn timeout)
    {:connections connections
     :server server
     :ddb-client ddb-client}))

(defn -main
  [config-file & args]
  (let [config (-> config-file slurp edn/read-string)
        server (start-server! config)]
    (prn server)
    server))
