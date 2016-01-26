(ns com.hello.messeji.client
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn])
  (:import
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]
    [com.hello.suripu.core.util HelloHttpHeader]
    [com.hello.suripu.service SignedMessage]))

(def test-key-bytes (.getBytes "1234567891234567"))

(defn localhost
  ([]
    (localhost "resources/config/dev.edn"))
  ([config-file-name]
    (let [port (-> config-file-name slurp edn/read-string :http :port)]
      (str "http://localhost:" port))))

(defn sign-protobuf
  "Given a protobuf object and the bytes of an aes key,
  return a valid signed message."
  [proto-message key-bytes]
  (let [body (.toByteArray proto-message)
        signed (-> body (SignedMessage/sign key-bytes) .get)
        iv (->> signed (take 16) byte-array)
        sig (->> signed (drop 16) (take 32) byte-array)]
    (byte-array (concat body iv sig))))

(defn- post
  [url sense-id body]
  @(http/post
    url
    {:body body
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
    (post url sense-id (.toByteArray message))))

(defn- receive-message-request
  [sense-id acked-message-ids]
  (let [builder (Messeji$ReceiveMessageRequest/newBuilder)]
    (.setSenseId builder sense-id)
    (doseq [id acked-message-ids]
      (.addMessageReadId builder id))
    (.build builder)))

(defn receive-messages
  [host sense-id acked-message-ids]
  (let [url (str host "/receive")
        request-proto (receive-message-request sense-id acked-message-ids)
        signed-proto (sign-protobuf request-proto test-key-bytes)]
    (-> (post url sense-id signed-proto)
      :body
      Messeji$BatchMessage/parseFrom)))

(defn- batch-message-ids
  [batch-message]
  (->> batch-message
    .getMessageList
    (map #(.getMessageId %))))

(defn start-sense
  ^java.io.Closeable [host sense-id callback-fn]
  (let [running (atom true)]
    (future
      (loop [message-ids []]
        (when @running
          (let [batch-message (receive-messages host sense-id message-ids)
                message-ids (batch-message-ids batch-message)]
            (when (seq message-ids)
              (callback-fn message-ids))
            (recur message-ids)))))
    (reify java.io.Closeable
      (close [this]
        (reset! running false)))))
