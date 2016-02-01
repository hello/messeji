(ns com.hello.messeji.protobuf
  (:import
    [com.hello.messeji.api
      Messeji$BatchMessage
      Messeji$Message
      Messeji$Message$Type
      Messeji$MessageStatus
      Messeji$MessageStatus$State
      Messeji$ReceiveMessageRequest
      SleepSounds$SleepSoundsCommand]))

(def message-type
  "Map of keywords to Message.Type objects."
  {:sleep-sounds Messeji$Message$Type/SLEEP_SOUNDS})

(defn message
  "Construct a Message from the given map.
  Optional fields need not be present in the map."
  ^Messeji$Message [{:keys [sender-id order message-id type sleep-sounds-command]}]
  (cond-> (Messeji$Message/newBuilder)
    sender-id (.setSenderId sender-id)
    :always (.setOrder order)
    message-id (.setMessageId message-id)
    :always (.setType type)
    sleep-sounds-command (.setSleepSoundsCommand sleep-sounds-command)
    :always .build))

(defn batch-message
  "Construct a BatchMessage from the given seq of Message objects."
  ^Messeji$BatchMessage [{:keys [messages]}]
  (let [builder (Messeji$BatchMessage/newBuilder)]
    (doseq [msg messages]
      (.addMessage builder ^Messeji$Message msg))
    (.build builder)))

(def message-status-state
  "Map of keywords to MessageStatus.State objects."
  {:pending Messeji$MessageStatus$State/PENDING
   :sent Messeji$MessageStatus$State/SENT
   :received Messeji$MessageStatus$State/RECEIVED
   :expired Messeji$MessageStatus$State/EXPIRED})

(defn message-status
  "Construct a MessageStatus from the given map."
  ^Messeji$MessageStatus [{:keys [message-id state]}]
  (.. (Messeji$MessageStatus/newBuilder)
    (setMessageId message-id)
    (setState state)
    build))

(defn receive-message-request
  "Construct a ReceiveMessageRequest from the given map."
  ^Messeji$ReceiveMessageRequest [{:keys [sense-id message-read-ids]}]
  (let [builder (Messeji$ReceiveMessageRequest/newBuilder)]
    (.setSenseId builder sense-id)
    (doseq [id message-read-ids]
      (.addMessageReadId builder id))
    (.build builder)))

(defn sleep-sounds-command
  ^SleepSounds$SleepSoundsCommand [m]
  (.build (SleepSounds$SleepSoundsCommand/newBuilder)))
