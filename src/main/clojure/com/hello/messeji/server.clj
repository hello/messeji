(ns com.hello.messeji.server
  (:gen-class)
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn]
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
      Messeji$MultiMessage]
    [com.hello.suripu.service SignedMessage]))

(defn handle-receive
  [connections key-store request]
  (let [sense-id (-> request :headers (get HelloHttpHeader/SENSE_ID))
        receive-message-request (Messeji$ReceiveMessageRequest/parseFrom
                                  (:body request))
        message (.. (Messeji$Message/newBuilder)
                  (setType Messeji$Message$Type/SLEEP_SOUNDS)
                  (setOrder 1)
                  build)
        multi-message (.. (Messeji$MultiMessage/newBuilder)
                        (addMessage message)
                        build)]
    {:status 200
     :body (bs/to-input-stream (.toByteArray multi-message))}))

(defn handler
  [connections key-store]
  (wrap-content-type
    (compojure/routes
      (POST "/receive" request (handle-receive connections key-store request)))))

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
        server (http/start-server
                 (handler connections key-store)
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
