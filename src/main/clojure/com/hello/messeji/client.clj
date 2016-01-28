(ns com.hello.messeji.client
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn]
    [com.hello.messeji.config :as config])
  (:import
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest
      Messeji$Message
      Messeji$Message$Type
      Messeji$BatchMessage]
    [com.hello.messeji SignedMessage]
    [org.apache.commons.codec.binary Hex]))

(defn localhost
  ([]
    (localhost "resources/config/dev.edn"))
  ([config-file-name]
    (let [port (get-in (config/read config-file-name) [:http :port])]
      (str "http://localhost:" port))))

(defn sign-protobuf
  "Given a protobuf object and the bytes of an aes key,
  return a valid signed message."
  [proto-message key]
  (let [body (.toByteArray proto-message)
        key-bytes (Hex/decodeHex (.toCharArray key))
        signed (-> body (SignedMessage/sign key-bytes) .get)
        iv (->> signed (take 16) byte-array)
        sig (->> signed (drop 16) (take 32) byte-array)]
    (byte-array (concat body iv sig))))

(defn- post
  [url sense-id body]
  @(http/post
    url
    {:body body
     :headers {"X-Hello-Sense-Id" sense-id}}))

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
  [host sense-id key acked-message-ids]
  (let [url (str host "/receive")
        request-proto (receive-message-request sense-id acked-message-ids)
        signed-proto (sign-protobuf request-proto key)]
    (-> (post url sense-id signed-proto)
      :body
      Messeji$BatchMessage/parseFrom)))

(defn- batch-message-ids
  [batch-message]
  (some->> batch-message
    .getMessageList
    (map #(.getMessageId %))))

(defn start-sense
  ^java.io.Closeable [host sense-id key callback-fn]
  (let [running (atom true)]
    (future
      (loop [message-ids []]
        (when @running
          (let [batch-message (try
                                (receive-messages host sense-id key message-ids)
                                (catch Exception e
                                  ;; Print the exception and sleep for a bit
                                  ;; before retrying.
                                  (prn e)
                                  (Thread/sleep 5000)))
                message-ids (batch-message-ids batch-message)]
            (when (seq message-ids)
              (callback-fn message-ids))
            (recur message-ids)))))
    (reify java.io.Closeable
      (close [this]
        (reset! running false)))))
