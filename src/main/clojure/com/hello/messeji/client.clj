(ns com.hello.messeji.client
  (:require
    [aleph.http :as http]
    [byte-streams :as bs])
  (:import
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]
    [com.hello.suripu.core.util HelloHttpHeader]))

(defn- post
  [url sense-id request]
  @(http/post
    url
    {:body (.toByteArray request)
     :headers {HelloHttpHeader/SENSE_ID sense-id}}))

(defn send-message
  [host sense-id]
  (let [url (str host "/send")
        order (System/nanoTime)
        message (.. (Messeji$Message/newBuilder)
                  (setSenderId "clj-client")
                  (setOrder order)
                  (setType Messeji$Message$Type/SLEEP_SOUNDS)
                  build)]
    (post url sense-id message)))

(defn receive-messages
  [host sense-id acked-message-ids]
  (let [url (str host "/receive")
        builder (.. (Messeji$ReceiveMessageRequest/newBuilder)
                  (setSenseId sense-id))]
    (doseq [id acked-message-ids]
      (.addMessageReadId builder id))
    (-> (post url sense-id (.build builder))
      :body
      Messeji$BatchMessage/parseFrom)))

(defn- batch-message-ids
  [batch-message]
  (->> batch-message
    .getMessageList
    (map #(.getMessageId %))))

(defn start-sense
  ^java.io.Closeable [host sense-id]
  (let [running (atom true)]
    (future
      (loop [message-ids []]
        (when @running
          (let [batch-message (receive-messages host sense-id message-ids)
                message-ids (batch-message-ids batch-message)]
            (when (seq message-ids) (prn message-ids))
            (recur message-ids)))))
    (reify java.io.Closeable
      (close [this]
        (reset! running false)))))
