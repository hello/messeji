(ns com.hello.messeji.db.in-mem
  "In-memory map of {message-id => message}.

  Schema:
  message-id => {
    :message,
    :sense-id,
    :sent?,
    :acknowledged?,
    :timestamp
  }")

(def database (ref {}))
(def latest-id (ref 0))

(defn mark-sent!
  [messages]
  ;; Currently a no-op
  nil)

(defn- add-message
  [db-map sense-id message-id message]
  (assoc db-map message-id
    {:message message
     :sense-id sense-id
     :sent? false
     :acknowledged? false
     ;; TODO use nanoTime
     :timestamp (System/currentTimeMillis)}))

(defn create-message!
  "Create message in database and return the created message."
  [sense-id message]
  (dosync
    (let [id (alter latest-id inc)
          message-with-id (.. message toBuilder (setMessageId id) build)]
      (alter database add-message sense-id id message-with-id)
      message-with-id)))

(defn unacked-messages
  "Retrieve all unacked messages for sense-id that are younger than timeout."
  [sense-id timeout-millis]
  (->> @database
    vals
    (filter #(and (= (:sense-id %) sense-id)
                  (> timeout-millis (- (System/currentTimeMillis) (:timestamp %)))
                  (not (:acknowledged? %))))
    (map :message)))

; (defn acknowledge!
;   [message-ids]
;   (dosync
;     (alter database assoc-in [message-id :acknowledged?] true))
;   (prn @database)
;   (@database message-id))
