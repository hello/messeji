(ns com.hello.messeji.protobuf
  (:import
    [com.hello.messeji.api
      Messeji$BatchMessage
      Messeji$Message
      Messeji$Message$Type
      Messeji$MessageStatus
      Messeji$MessageStatus$State
      Messeji$ReceiveMessageRequest
      AudioCommands$PlayAudio
      AudioCommands$StopAudio]
    [java.io InputStream]))


(defprotocol MessejiProtobuf
  (message ^Messeji$Message [this])

  (batch-message ^Messeji$BatchMessage [this])

  (message-status ^Messeji$MessageStatus [this])

  (receive-message-request ^Messeji$ReceiveMessageRequest [this])

  (play-audio ^AudioCommands$PlayAudio [this])

  (stop-audio ^AudioCommands$StopAudio [this]))


;; Maps (message {:sender-id "sender1", ...})
(extend-type clojure.lang.IPersistentMap
  MessejiProtobuf

  (message
    [{:keys [sender-id order message-id type play-audio stop-audio]}]
    (cond-> (Messeji$Message/newBuilder)
      sender-id (.setSenderId sender-id)
      :always (.setOrder order)
      message-id (.setMessageId message-id)
      :always (.setType type)
      play-audio (.setPlayAudio play-audio)
      stop-audio (.setStopAudio stop-audio)
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

  (play-audio
    [{:keys [file-path volume-percent duration-seconds
             fade-out-duration-seconds fade-in-duration-seconds]}]
    (let [builder (AudioCommands$PlayAudio/newBuilder)]
      (.. builder
        (setFilePath file-path)
        (setVolumePercent volume-percent)
        (setFadeOutDurationSeconds fade-out-duration-seconds)
        (setFadeInDurationSeconds fade-in-duration-seconds))
      (when duration-seconds
        (.setDurationSeconds duration-seconds))
      (.build builder)))

  (stop-audio
    [{:keys [fade-out-duration-seconds]}]
    (.. (AudioCommands$StopAudio/newBuilder)
      (setFadeOutDurationSeconds fade-out-duration-seconds)
      build)))


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

  (play-audio
    [bytes]
    (AudioCommands$PlayAudio/parseFrom ^bytes bytes))

  (stop-audio
    [bytes]
    (AudioCommands$StopAudio/parseFrom ^bytes bytes)))


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

  (play-audio
    [stream]
    (AudioCommands$PlayAudio/parseFrom ^InputStream stream))

  (stop-audio
    [stream]
    (AudioCommands$StopAudio/parseFrom ^InputStream stream)))


(def message-type
  "Map of keywords to Message.Type objects."
  {:play-audio Messeji$Message$Type/PLAY_AUDIO
   :stop-audio Messeji$Message$Type/STOP_AUDIO})

(def message-status-state
  "Map of keywords to MessageStatus.State objects."
  {:pending Messeji$MessageStatus$State/PENDING
   :sent Messeji$MessageStatus$State/SENT
   :received Messeji$MessageStatus$State/RECEIVED
   :expired Messeji$MessageStatus$State/EXPIRED})
