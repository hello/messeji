(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]
    [com.hello.messeji.config :as messeji-config]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.db.in-mem :as mem]
    [com.hello.messeji.db.key-store-ddb :as ksddb]
    [com.hello.messeji.middleware :as middleware]
    [compojure.core :as compojure :refer [GET POST]]
    [compojure.route :as route]
    [manifold.deferred :as deferred]
    [ring.middleware.params :as params]
    [ring.middleware.content-type :refer [wrap-content-type]])
  (:import
    [com.amazonaws ClientConfiguration]
    [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
    [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
    [com.hello.messeji SignedMessage]
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]))

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

(defn- mark-sent
  [message-store messages]
  (db/mark-sent
    message-store
    (map #(.getMessageId ^Messeji$Message %) messages)))

(defn- receive-messages
  [connections-atom message-store timeout sense-id]
  (if-let [unacked-messages (seq (db/unacked-messages message-store sense-id))]
    (do
      (mark-sent message-store unacked-messages)
      (batch-message-response unacked-messages))
    (let [deferred-response (deferred/deferred)]
      (swap! connections-atom assoc sense-id deferred-response)
      (deferred/timeout!
        deferred-response
        timeout
        (batch-message-response [])))))

(defn- request-sense-id
  [request]
  (let [sense-id (-> request :headers (get "X-Hello-Sense-Id"))]
    (or sense-id (middleware/throw-invalid-request))))

(defn- acked-message-ids
  [^Messeji$ReceiveMessageRequest receive-message-request]
  (seq (.getMessageReadIdList receive-message-request)))

(defn- valid-key?
  [signed-message key]
  (let [error-optional (.validateWithKey signed-message key)
        error? (.isPresent error-optional)]
    (when error?
      (log/debug (-> error-optional .get .message)))
    (not error?)))

(defn- valid-message?
  [key-store sense-id signed-message]
  (let [key (db/get-key key-store sense-id)]
    (when-not key
      (middleware/throw-invalid-request))
    (valid-key? signed-message key)))

(defn- ack-and-receive
  [connections message-store timeout receive-message-request]
  (db/acknowledge message-store (acked-message-ids receive-message-request))
  (receive-messages connections message-store timeout (.getSenseId receive-message-request)))

(defn handle-receive
  [connections key-store message-store timeout request]
  (let [sense-id (request-sense-id request)
        signed-message (-> request :body bs/to-byte-array SignedMessage/parse)
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (.body signed-message))]
    (when-not (= sense-id (.getSenseId receive-message-request))
      (middleware/throw-invalid-request))
    (if (valid-message? key-store sense-id signed-message)
      (ack-and-receive connections message-store timeout receive-message-request)
      {:status 401, :body ""})))

(defn- send-messages
  [message-store connection-deferred messages]
  (when (and connection-deferred
             (not (deferred/realized? connection-deferred)))
    (when-let [delivery (deferred/success!
                          connection-deferred
                          (batch-message-response messages))]
      ;; If delivery is true, then we haven't previously delivered anything
      ;; and the connection hasn't yet been timed out.
      (mark-sent message-store messages)
      delivery)))

(defn handle-send
  [connections-atom message-store request]
  (let [sense-id (request-sense-id request)
        message (Messeji$Message/parseFrom (:body request))
        message-with-id (db/create-message message-store sense-id message)]
    (send-messages message-store (get @connections-atom sense-id) [message-with-id])
    {:status 201
     :body message-with-id}))

(defn handler
  [connections key-store message-store timeout]
  (let [routes
        (compojure/routes
          (GET "/healthz" _ {:status 200 :body "ok"})
          (POST "/receive" request
            (handle-receive connections key-store message-store timeout request))
          (POST "/send" request
            (handle-send connections message-store request))
          (route/not-found ""))]
    (-> routes
       middleware/wrap-log-request
       middleware/wrap-protobuf-request
       middleware/wrap-protobuf-response
       middleware/wrap-invalid-request
       wrap-content-type
       middleware/wrap-500)))

(defrecord Service
  [connections server ddb-clients]

  java.io.Closeable
  (close [this]
    ;; Calls NioEventLoopGroup.shutdownGracefully()
    (.close server)
    (doseq [[_ client] ddb-clients]
      (.shutdown client))
    (reset! connections nil)))

(defn start-server
  "Performs setup, starts server, and returns a java.io.Closeable record."
  [config-map]
  (let [connections (atom {})
        credentials-provider (DefaultAWSCredentialsProviderChain.)
        client-config (.. (ClientConfiguration.)
                        (withConnectionTimeout 200)
                        (withMaxErrorRetry 1)
                        (withMaxConnections 100))
        ks-ddb-client (doto (AmazonDynamoDBClient. credentials-provider client-config)
                        (.setEndpoint (get-in config-map [:key-store :endpoint])))
        key-store (ksddb/key-store
                    (get-in config-map [:key-store :table])
                    ks-ddb-client)
        timeout (get-in config-map [:http :receive-timeout])
        message-store (mem/mk-message-store (:max-message-age-millis config-map))
        server (http/start-server
                 (handler connections key-store message-store timeout)
                 {:port (get-in config-map [:http :port])})]
    (->Service
      connections
      server
      {:key-store ks-ddb-client})))

(defn -main
  [config-file & args]
  (let [config (apply messeji-config/read config-file args)
        server (start-server config)]
    (log/debug "Using the following config: " config)
    (log/debug "Server: " server)
    server))
