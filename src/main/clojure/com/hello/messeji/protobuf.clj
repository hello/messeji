(ns com.hello.messeji.protobuf
  (:import
    [com.hello.messeji.api
      Messeji$BatchMessage
      Messeji$Message
      Messeji$Message$Type
      Messeji$MessageStatus
      Messeji$MessageStatus$State
      Messeji$ReceiveMessageRequest
      SleepSounds$SleepSoundsCommand]
    [java.io InputStream]))


(defprotocol MessejiProtobuf
  (message ^Messeji$Message [this])

  (batch-message ^Messeji$BatchMessage [this])

  (message-status ^Messeji$MessageStatus [this])

  (receive-message-request ^Messeji$ReceiveMessageRequest [this])

  (sleep-sounds-command ^SleepSounds$SleepSoundsCommand [this]))


;; Maps (message {:sender-id "sender1", ...})
(extend-type clojure.lang.IPersistentMap
  MessejiProtobuf

  (message
    [{:keys [sender-id order message-id type sleep-sounds-command]}]
    (cond-> (Messeji$Message/newBuilder)
      sender-id (.setSenderId sender-id)
      :always (.setOrder order)
      message-id (.setMessageId message-id)
      :always (.setType type)
      sleep-sounds-command (.setSleepSoundsCommand sleep-sounds-command)
      :always .build))

  (batch-message
    [{:keys [messages]}]
    (let [builder (Messeji$BatchMessage/newBuilder)]
      (doseq [msg messages]
        (.addMessage builder ^Messeji$Message msg))
      (.build builder)))

  (message-status
    [{:keys [message-id state]}]
    (.. (Messeji$MessageStatus/newBuilder)
      (setMessageId message-id)
      (setState state)
      build))

  (receive-message-request
    [{:keys [sense-id message-read-ids]}]
    (let [builder (Messeji$ReceiveMessageRequest/newBuilder)]
      (.setSenseId builder sense-id)
      (doseq [id message-read-ids]
        (.addMessageReadId builder id))
      (.build builder)))

  (sleep-sounds-command
    [m]
    (.build (SleepSounds$SleepSoundsCommand/newBuilder))))


;; byte[]
(extend-type (Class/forName "[B")
  MessejiProtobuf

  (message
    [bytes]
    (Messeji$Message/parseFrom ^bytes bytes))

  (batch-message
    [bytes]
    (Messeji$BatchMessage/parseFrom ^bytes bytes))

  (message-status
    [bytes]
    (Messeji$MessageStatus/parseFrom ^bytes bytes))

  (receive-message-request
    [bytes]
    (Messeji$ReceiveMessageRequest/parseFrom ^bytes bytes))

  (sleep-sounds-command
    [bytes]
    (SleepSounds$SleepSoundsCommand/parseFrom ^bytes bytes)))


(extend-type InputStream
  MessejiProtobuf

  (message
    [stream]
    (Messeji$Message/parseFrom ^InputStream stream))

  (batch-message
    [stream]
    (Messeji$BatchMessage/parseFrom ^InputStream stream))

  (message-status
    [stream]
    (Messeji$MessageStatus/parseFrom ^InputStream stream))

  (receive-message-request
    [stream]
    (Messeji$ReceiveMessageRequest/parseFrom ^InputStream stream))

  (sleep-sounds-command
    [stream]
    (SleepSounds$SleepSoundsCommand/parseFrom ^InputStream stream)))


(def message-type
  "Map of keywords to Message.Type objects."
  {:sleep-sounds Messeji$Message$Type/SLEEP_SOUNDS})

(def message-status-state
  "Map of keywords to MessageStatus.State objects."
  {:pending Messeji$MessageStatus$State/PENDING
   :sent Messeji$MessageStatus$State/SENT
   :received Messeji$MessageStatus$State/RECEIVED
   :expired Messeji$MessageStatus$State/EXPIRED})
