(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]
    [com.hello.messeji
      [config :as messeji-config]
      [db :as db]
      [metrics :as metrics]
      [middleware :as middleware]
      [protobuf :as pb]
      [pubsub :as pubsub]]
    [com.hello.messeji.db
      [redis :as redis]
      [key-store-ddb :as ksddb]]
    [compojure.core :as compojure :refer [GET POST]]
    [compojure.response :refer [Renderable]]
    [compojure.route :as route]
    [manifold.deferred :as deferred])
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

;; Compojure will normally dereference deferreds and return the realized value.
;; This unfortunately blocks the thread. Since aleph can accept the un-realized
;; deferred, we extend compojure's Renderable protocol to pass the deferred
;; through unchanged so that the thread won't be blocked.
;; See https://github.com/ztellman/aleph/issues/216
(extend-protocol Renderable
  manifold.deferred.Deferred
  (render [d _] d))

(defn- batch-message-response
  [^bytes key messages]
  (let [signed-response-opt (-> {:messages messages}
                              pb/batch-message
                              .toByteArray
                              (SignedMessage/sign key))]
    (if (.isPresent signed-response-opt)
      {:body (.get signed-response-opt)
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
    (or sense-id (middleware/throw-invalid-request "No X-Hello-Sense-Id header."))))

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
  (let [message-ids (acked-message-ids receive-message-request)]
    (metrics/mark "server.acked-messages" (count message-ids))
    (db/acknowledge message-store message-ids)
    (receive-messages connections message-store timeout (.getSenseId receive-message-request) key)))

(defn- parse-receive-request
  [request-bytes]
  (try
    (SignedMessage/parse request-bytes)
    (catch RuntimeException e
      (middleware/throw-invalid-request e))))

(defn handle-receive
  [connections key-store message-store timeout request]
  (let [sense-id (request-sense-id request)
        signed-message (-> request :body bs/to-byte-array parse-receive-request)
        receive-message-request (pb/receive-message-request
                                  (.body signed-message))
        key (get-key-or-throw key-store sense-id)]
    (when-not (= sense-id (.getSenseId receive-message-request))
      (middleware/throw-invalid-request
        (str "Sense ID in header is " sense-id
             " but in body is " (.getSenseId receive-message-request))))
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
  [message-store request pubsub-conn-opts]
  (let [sense-id (request-sense-id request)
        message (pb/message (:body request))
        message-with-id (db/create-message message-store sense-id message)]
    (pubsub/publish pubsub-conn-opts sense-id message-with-id)
    {:status 201
     :body message-with-id}))

(defn- parse-message-id
  [message-id]
  (try
    (Long/parseLong message-id)
    (catch java.lang.NumberFormatException e
      (middleware/throw-invalid-request))))

(defn handle-status
  [message-store message-id]
  (if-let [message-status (db/get-status message-store (parse-message-id message-id))]
    {:status 200, :body message-status}
    {:status 404, :body ""}))

(defn- mk-routes
  "Create a compojure `routes` handler with default common health check and 404 routes."
  [& routes]
  (apply
    compojure/routes
    (concat
      routes
      [(GET "/healthz" _ {:status 200 :body "ok"}) ; ELB health check
       (route/not-found "")]))) ; 404

 (defn- wrap-routes
   "Wrap the given routes handler in common middleware."
   [routes]
   (-> routes
       middleware/wrap-mark-request-meter
       middleware/wrap-log-request
       middleware/wrap-protobuf-request
       middleware/wrap-protobuf-response
       middleware/wrap-invalid-request
       middleware/wrap-content-type
       middleware/wrap-500))

 ;; Define timed versions of all the handlers.
 (metrics/deftimed handle-send-timed server handle-send)
 (metrics/deftimed handle-receive-timed server handle-receive)
 (metrics/deftimed handle-status-timed server handle-status)

(defn receive-handler
  "Request handler for the subscriber (receive) endpoints."
  [connections key-store message-store timeout]
  (let [routes
        (mk-routes
          (POST "/receive" request
            (handle-receive-timed connections key-store message-store timeout request)))]
    (wrap-routes routes)))

(defn publish-handler
  "Request handler for the publish (send) endpoints."
  [message-store pubsub-conn-opts]
  (let [routes
        (mk-routes
          (POST "/send" request
            (handle-send-timed message-store request pubsub-conn-opts))
          (GET "/status/:message-id" [message-id]
            (handle-status-timed message-store message-id)))]
    (wrap-routes routes)))

(defn pubsub-handler
  "Returns a function that takes a sense-id and message. This is invoked when a
  new subscribed message arrives."
  [connections-atom message-store]
  (fn [sense-id message]
    ;; TODO see if message already delivered?
    ;; TODO do not want to do this in subscribing thread...
    (send-messages message-store (@connections-atom sense-id) [message])))

;; Wrapper for service state.
(defrecord Service
  [config connections pub-server sub-server listener metric-reporter data-stores]
  java.io.Closeable
  (close [this]
    ;; Calls NioEventLoopGroup.shutdownGracefully()
    (.close pub-server)
    (.close sub-server)
    (doseq [[_ store] data-stores]
      (.close store))
    (.close listener)
    (when metric-reporter
      (.close metric-reporter))
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
  (log/info "Starting server")
  (let [connections (atom {})
        credentials-provider (DefaultAWSCredentialsProviderChain.)
        client-config (.. (ClientConfiguration.)
                        (withConnectionTimeout 200)
                        (withMaxErrorRetry 1)
                        (withMaxConnections 1000))
        ks-ddb-client (doto (AmazonDynamoDBClient. credentials-provider client-config)
                        (.setEndpoint (get-in config-map [:key-store :endpoint])))
        key-store (ksddb/key-store
                    (get-in config-map [:key-store :table])
                    ks-ddb-client)
        timeout (get-in config-map [:http :receive-timeout])
        redis-spec (get-in config-map [:redis :spec])
        message-store (redis/mk-message-store
                        {:spec redis-spec}
                        (:max-message-age-millis config-map)
                        (get-in config-map [:redis :delete-after-seconds]))
        listener (pubsub/subscribe redis-spec (pubsub-handler connections message-store))
        pub-server (http/start-server
                    (publish-handler message-store {:spec redis-spec})
                    {:port (get-in config-map [:http :pub-port])})
        sub-server (http/start-server
                    (receive-handler connections key-store message-store timeout)
                    {:port (get-in config-map [:http :sub-port])})
        graphite-config (:graphite config-map)
        reporter (if (:enabled? graphite-config)
                  (do ;; then
                    (log/info "Metrics enabled.")
                    (metrics/start-graphite! graphite-config))
                  (do ;; else
                    (log/warn "Metrics not enabled")
                    nil))]
    (->Service
      config-map
      connections
      pub-server
      sub-server
      listener
      reporter
      {:key-store key-store
       :message-store message-store})))

(defn- shutdown-handler
  [^Service service]
  (fn []
    (log/info "Shutting down...")
    (.close service)
    (log/info "Service shutdown complete.")))

(defn -main
  [config-file & args]
  (let [config (apply messeji-config/read config-file args)
        server (start-server config)]
    (log/info "Using the following config: " config)
    (log/info "Server: " server)
    (.addShutdownHook (Runtime/getRuntime) (Thread. (shutdown-handler server)))
    server))
