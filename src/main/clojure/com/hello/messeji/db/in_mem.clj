(ns com.hello.messeji.db.in-mem
  "In-memory map of {message-id => message}.

  Schema:
  message-id => {
    :message,
    :sense-id,
    :sent?,
    :acknowledged?,
    :timestamp
  }"
  (:require
    [com.hello.messeji.db :as db]))

(defn- add-message
  [db-map sense-id message-id message]
  (assoc db-map message-id
    {:message message
     :sense-id sense-id
     :sent? false
     :acknowledged? false
     :timestamp (System/nanoTime)}))

(defn- ack-message-ids
  [db-map message-ids]
  (reduce #(assoc-in %1 [%2 :acknowledged?] true) db-map message-ids))

(defrecord InMemoryMessageStore
  [database-ref latest-id-ref max-message-age-millis]

  db/MessageStore
  (db/create-message
    [_ sense-id message]
    (dosync
      (let [id (alter latest-id-ref inc)
            message-with-id (.. message toBuilder (setMessageId id) build)]
        (alter database-ref add-message sense-id id message-with-id)
        message-with-id)))

  (db/unacked-messages
    [_ sense-id]
    (->> @database-ref
      vals
      (filter #(and (= (:sense-id %) sense-id)
                    (> (* max-message-age-millis 1000000)
                      (- (System/nanoTime) (:timestamp %)))
                    (not (:acknowledged? %))))
      (map :message)))

  (db/acknowledge
    [_ message-ids]
    (dosync
      (alter database-ref ack-message-ids message-ids))))

(defn mk-message-store
  [max-message-age-millis]
  (->InMemoryMessageStore (ref {}) (ref 0) max-message-age-millis))
