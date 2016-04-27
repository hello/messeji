(ns com.hello.messeji.server
  "The application lifecycle logic, setup/teardown connections, etc.
  This is also the main runnable namespace for messeji-server.

  Run with:
  > lein run -m com.hello.messeji.server path/to/config.edn"
  (:gen-class)
  (:require
    [aleph.http :as http]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [com.hello.messeji
      [config :as messeji-config]
      [connection :as conn]
      [handlers :as handlers]
      [kinesis :as kinesis]
      [metrics :as metrics]
      [pubsub :as pubsub]]
    [com.hello.messeji.db
      [redis :as redis]
      [key-store-ddb :as ksddb]]
    [compojure.response :refer [Renderable]]
    [manifold.deferred :as deferred])
  (:import
    [com.amazonaws ClientConfiguration]
    [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
    [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
    [org.apache.log4j PropertyConfigurator]))

;; Compojure will normally dereference deferreds and return the realized value.
;; This unfortunately blocks the thread. Since aleph can accept the un-realized
;; deferred, we extend compojure's Renderable protocol to pass the deferred
;; through unchanged so that the thread won't be blocked.
;; See https://github.com/ztellman/aleph/issues/216
(extend-protocol Renderable
  manifold.deferred.Deferred
  (render [d _] d))


;; Wrapper for service state.
(defrecord Service
  [config connections pub-server sub-server listener metric-reporter data-stores request-log-producer]
  java.io.Closeable
  (close [this]
    ;; Calls NioEventLoopGroup.shutdownGracefully()
    (.close pub-server)
    (.close sub-server)
    (doseq [[_ store] data-stores]
      (.close store))
    (.close listener)
    (when metric-reporter
      (.close metric-reporter))))

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
  (let [credentials-provider (DefaultAWSCredentialsProviderChain.)
        client-config (.. (ClientConfiguration.)
                        (withConnectionTimeout 200)
                        (withMaxErrorRetry 1)
                        (withMaxConnections 1000))
        ks-ddb-client (doto (AmazonDynamoDBClient. credentials-provider client-config)
                        (.setEndpoint (get-in config-map [:key-store :endpoint])))
        request-log-producer (kinesis/stream-producer
                              (get-in config-map [:request-log :stream])
                              (get-in config-map [:request-log :log-level]))
        key-store (ksddb/key-store
                    (get-in config-map [:key-store :table])
                    ks-ddb-client)
        timeout (get-in config-map [:http :receive-timeout])
        connections (conn/sense-connections timeout)
        redis-spec (get-in config-map [:redis :spec])
        message-store (redis/mk-message-store
                        {:spec redis-spec}
                        (:max-message-age-millis config-map)
                        (get-in config-map [:redis :delete-after-seconds]))
        listener (pubsub/subscribe redis-spec (handlers/pubsub-handler connections message-store))
        ;; TODO just passing the Service into each handler would be cleaner.
        pub-server (http/start-server
                    (handlers/publish-handler message-store {:spec redis-spec} request-log-producer)
                    {:port (get-in config-map [:http :pub-port])})
        sub-server (http/start-server
                    (handlers/receive-handler connections key-store message-store request-log-producer)
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
       :message-store message-store}
      request-log-producer)))

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
