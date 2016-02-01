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
    [com.hello.messeji.db :as db])
  (:import
    [com.hello.messeji.api
      Messeji$MessageStatus
      Messeji$MessageStatus$State]))

(defn- timestamp
  []
  (System/nanoTime))

(defn- add-message
  [db-map sense-id message-id message]
  (assoc db-map message-id
    {:message message
     :sense-id sense-id
     :sent? false
     :acknowledged? false
     :timestamp (timestamp)}))

(defn- assoc-in-message-ids
  [db-map k v message-ids]
  (reduce #(assoc-in %1 [%2 k] v) db-map message-ids))

(defn- expired?
  ([max-age message-timestamp]
    (expired? max-age message-timestamp (timestamp)))
  ([max-age message-timestamp now]
    (> (- now message-timestamp) max-age)))

(defn- message-state
  [{:keys [message sent? acknowledged? timestamp]} max-age]
  (cond
    acknowledged? Messeji$MessageStatus$State/RECEIVED
    sent? Messeji$MessageStatus$State/SENT
    (expired? max-age timestamp) Messeji$MessageStatus$State/EXPIRED
    :else Messeji$MessageStatus$State/PENDING))

(defn- message-status
  [message-id state]
  (.. (Messeji$MessageStatus/newBuilder)
    (setMessageId message-id)
    (setState state)
    build))

(defrecord InMemoryMessageStore
  [database-ref latest-id-ref max-message-age-nanos]

  db/MessageStore
  (create-message
    [_ sense-id message]
    (dosync
      (let [id (alter latest-id-ref inc)
            message-with-id (.. message toBuilder (setMessageId id) build)]
        (alter database-ref add-message sense-id id message-with-id)
        message-with-id)))

  (unacked-messages
    [_ sense-id]
    (->> @database-ref
      vals
      (filter #(and (= (:sense-id %) sense-id)
                    (not (expired? max-message-age-nanos (:timestamp %)))
                    (not (:acknowledged? %))))
      (map :message)))

  (get-status
    [_ message-id]
    (when-let [state (some-> @database-ref
                       (get message-id)
                       (message-state max-message-age-nanos))]
      (message-status message-id state)))

  (mark-sent
    [_ message-ids]
    (dosync
      (alter database-ref assoc-in-message-ids :sent? true message-ids)))

  (acknowledge
    [_ message-ids]
    (dosync
      (alter database-ref assoc-in-message-ids :acknowledged? true message-ids))))

(defn mk-message-store
  [max-message-age-millis]
  (->InMemoryMessageStore (ref {}) (ref 0) (* max-message-age-millis 1000000)))
