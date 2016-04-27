(ns com.hello.messeji.connection
  "Abstraction for managing a bunch of sense connections."
  (:require
    [manifold.deferred :as d]))

(defprotocol SenseConnections

  (add [this sense-id]
    "Add this sense-id as a new connection and return a Deferred representing the response.")

  (respond [this sense-id response]
    "Respond to the sense connection referenced by sense-id."))

(defrecord AtomicTimeoutSenseConnections
  [connections-atom timeout]

  SenseConnections
  (add [_ sense-id]
    (let [response-deferred (d/deferred)]
      (swap! connections-atom assoc sense-id response-deferred)
        (d/timeout!
          response-deferred
          timeout
          [])))

  (respond [_ sense-id response]
    (when-let [response-deferred (@connections-atom sense-id)]
      (d/success! response-deferred response)))


  java.io.Closeable
  (close [_]
    (reset! connections-atom {})))

(defn sense-connections
  "Return a new SenseConnections with a server timeout of `timeout` ms."
  [timeout]
  (->AtomicTimeoutSenseConnections (atom {}) timeout))
