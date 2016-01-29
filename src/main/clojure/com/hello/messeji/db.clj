(ns com.hello.messeji.db)

(defprotocol MessageStore

  (create-message
    [this sense-id message]
    "Create message in database and return the created message.")

  (unacked-messages
    [this sense-id]
    "Retrieve all unacked messages for sense-id that are younger than timeout.")

  (get-status
    [this message-id]
    "Retrieve a MessageStatus for the given message-id.")

  (mark-sent
    [this message-ids]
    "Mark all message-ids as sent.")

  (acknowledge
    [this message-ids]
    "Mark all message-ids as acknowledged.
    Now these messages won't be retrieved from `unacked-messages`."))

(defprotocol KeyStore
  (get-key
    [this sense-id]
    "Get and decode a key from the key store for the sense-id."))
