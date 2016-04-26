(ns com.hello.messeji.messaging
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [com.hello.messeji
      [db :as db]
      [metrics :as metrics]
      [protobuf :as pb]]
    [manifold.deferred :as d])
  (:import
    [com.hello.messeji.api Messeji$Message]))

(defrecord SenseConnection [deferred-response sense-id])

(defn- mark-sent
  [message-store messages]
  (db/mark-sent
    message-store
    (map #(.getMessageId ^Messeji$Message %) messages)))

(defn receive-messages
  [connections-atom message-store timeout sense-id]
  (let [deferred-response (d/deferred)]
    (swap! connections-atom assoc sense-id (->SenseConnection deferred-response sense-id))
    (if-let [unacked-messages (metrics/time "db-unacked-messages" (seq (db/unacked-messages message-store sense-id)))]
      (do
        (log/infof "fn=receive-messages sense-id=%s unacked-messages-count=%s"
          sense-id (count unacked-messages))
        (mark-sent message-store unacked-messages)
        unacked-messages)
      (d/timeout!
        deferred-response
        timeout
        []))))

(defn ack-messages
  [message-store acked-message-ids sense-id]
  (metrics/mark "acked-messages" (count acked-message-ids))
  (when (seq acked-message-ids)
    (log/infof "fn=ack-and-receive sense-id=%s received-messages=%s"
      sense-id (string/join ":" acked-message-ids))
    (metrics/time "db-acknowledge" (db/acknowledge message-store acked-message-ids))))

(defn send-messages
  [message-store {:keys [deferred-response sense-id]} messages]
  (when (and deferred-response
             (not (d/realized? deferred-response)))
    (when-let [delivery (d/success!
                          deferred-response
                          messages)]
      (log/infof "fn=send-messages sense-id=%s delivered-messages-count=%s"
        sense-id (count messages))
      ;; If delivery is true, then we haven't previously delivered anything
      ;; and the connection hasn't yet been timed out.
      (mark-sent message-store messages)
      delivery)))
