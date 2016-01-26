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
    [com.google.protobuf
      InvalidProtocolBufferException
      Message]
    [com.hello.suripu.core.db KeyStoreDynamoDB]
    [com.hello.suripu.core.util HelloHttpHeader]
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]
    [com.hello.suripu.service SignedMessage]))

(def ^:private response-400
  {:status 400
   :body ""})

(defn wrap-protobuf-request
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch InvalidProtocolBufferException e
        response-400))))

(defn wrap-protobuf-response
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (instance? Message (:body response))
        (update-in response [:body] #(-> % .toByteArray bs/to-input-stream))
        response))))

(defn wrap-invalid-request
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= ::invalid-request (-> e ex-data ::type))
          response-400
          (throw e))))))

(defn wrap-500
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :body ""}))))

(defn throw-invalid-request
  []
  (throw (ex-info "Invalid request." {::type ::invalid-request})))

(defn- batch-message
  [messages]
  (let [builder (Messeji$BatchMessage/newBuilder)]
    (doseq [msg messages]
      (.addMessage builder msg))
    (.build builder)))

(defn- batch-message-response
  [messages]
  {:body (batch-message messages)
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
  (let [sense-id (-> request :headers (get HelloHttpHeader/SENSE_ID))]
    (or sense-id (throw-invalid-request))))

(defn- acked-message-ids
  [^Messeji$ReceiveMessageRequest receive-message-request]
  (seq (.getMessageReadIdList receive-message-request)))

(defn handle-receive
  [connections key-store message-store timeout request]
  (let [sense-id (request-sense-id request)
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (:body request))
        message-ids (acked-message-ids receive-message-request)]
    (when-not (= sense-id (.getSenseId receive-message-request))
      (throw-invalid-request))
    (db/acknowledge message-store message-ids)
    (receive-messages connections message-store timeout sense-id)))

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
  (let [sense-id (request-sense-id request)
        message (Messeji$Message/parseFrom (:body request))
        message-with-id (db/create-message message-store sense-id message)]
    (send-messages! (get @connections-atom sense-id) [message-with-id])
    {:status 201
     :body message-with-id}))

(defn handler
  [connections key-store message-store timeout]
  (let [routes
        (compojure/routes
          (GET "/healthz" {:status 200 :body "ok"})
          (POST "/receive" request
            (handle-receive connections key-store message-store timeout request))
          (POST "/send" request
            (handle-send connections message-store request)))]
    (-> routes
       wrap-protobuf-request
       wrap-protobuf-response
       wrap-invalid-request
       wrap-content-type
       wrap-500)))

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
