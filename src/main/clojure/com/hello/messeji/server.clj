(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.java.io :as io]
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
      Messeji$BatchMessage]
    [org.apache.log4j PropertyConfigurator]))

(defn- batch-message
  ^Messeji$BatchMessage [messages]
  (let [builder (Messeji$BatchMessage/newBuilder)]
    (doseq [msg messages]
      (.addMessage builder msg))
    (.build builder)))

(defn- sign
  [^bytes key batch-message]
  (let [signed-response-opt (SignedMessage/sign (.toByteArray batch-message) key)]))

(defn- batch-message-response
  [^bytes key messages]
  (let [signed-response-opt (-> messages
                              batch-message
                              .toByteArray
                              (SignedMessage/sign key))]
    (if (.isPresent signed-response-opt)
      {:body (bs/to-input-stream (.get signed-response-opt))
       :status 200}
      (do
        (log/error "Failed signing message.")
        {:status 500 :body ""}))))

(defn- mark-sent
  [message-store messages]
  (db/mark-sent
    message-store
    (map #(.getMessageId ^Messeji$Message %) messages)))

(defn- deferred-connection
  [deferred-response key]
  {:deferred-response deferred-response
   :key key})

(defn- receive-messages
  [connections-atom message-store timeout sense-id key]
  (if-let [unacked-messages (seq (db/unacked-messages message-store sense-id))]
    (do
      (mark-sent message-store unacked-messages)
      (batch-message-response key unacked-messages))
    (let [deferred-response (deferred/deferred)]
      (swap! connections-atom assoc sense-id (deferred-connection deferred-response key))
      (deferred/timeout!
        deferred-response
        timeout
        (batch-message-response key [])))))

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
      (log/error (-> error-optional .get .message)))
    (not error?)))

(defn- get-key-or-throw
  [key-store sense-id]
  (let [key (db/get-key key-store sense-id)]
    (when-not key
      (log/error "Key not found for sense-id" sense-id)
      (middleware/throw-invalid-request))
    key))

(defn- ack-and-receive
  [connections message-store timeout receive-message-request key]
  (db/acknowledge message-store (acked-message-ids receive-message-request))
  (receive-messages connections message-store timeout (.getSenseId receive-message-request) key))

(defn handle-receive
  [connections key-store message-store timeout request]
  (let [sense-id (request-sense-id request)
        signed-message (-> request :body bs/to-byte-array SignedMessage/parse)
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (.body signed-message))
        key (get-key-or-throw key-store sense-id)]
    (when-not (= sense-id (.getSenseId receive-message-request))
      (middleware/throw-invalid-request))
    (if (valid-key? signed-message key)
      (ack-and-receive connections message-store timeout receive-message-request key)
      {:status 401, :body ""})))

(defn- send-messages
  [message-store {:keys [deferred-response key]} messages]
  (when (and deferred-response
             (not (deferred/realized? deferred-response)))
    (when-let [delivery (deferred/success!
                          deferred-response
                          (batch-message-response key messages))]
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

(defn- configure-logging
  [{:keys [property-file-name properties]}]
  (with-open [reader (io/reader (io/resource property-file-name))]
    (let [prop (doto (java.util.Properties.)
                  (.load reader)
                  (.put "LOG_LEVEL" (:log-level properties)))]
      (PropertyConfigurator/configure prop))))

(defn start-server
  "Performs setup, starts server, and returns a java.io.Closeable record."
  [config-map]
  (configure-logging (:logging config-map))
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
    (log/info "Using the following config: " config)
    (log/info "Server: " server)
    server))
