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
  "Reads the config file (default is dev.edn) and concatenate the port from
  that config with localhost."
  ([]
    (localhost "resources/config/dev.edn"))
  ([config-file-name]
    (let [port (get-in (config/read config-file-name) [:http :port])]
      (str "http://localhost:" port))))

(defn sign-protobuf
  "Given a protobuf object and a key, return a valid signed message."
  [proto-message key]
  (let [body (.toByteArray proto-message)
        key-bytes (Hex/decodeHex (.toCharArray key))
        signed (-> body (SignedMessage/sign key-bytes) .get)
        iv (take 16 signed)
        sig (->> signed (drop 16) (take 32))]
    (byte-array (concat body iv sig))))

(defn- post
  [url sense-id body]
  @(http/post
    url
    {:body body
     :headers {"X-Hello-Sense-Id" sense-id}}))

(defn send-message
  "Send a message to the given sense-id,
  returning a Message object from the server."
  [host sense-id]
  (let [url (str host "/send")
        order (System/nanoTime)
        message (.. (Messeji$Message/newBuilder)
                  (setSenderId "clj-client")
                  (setOrder order)
                  (setType Messeji$Message$Type/SLEEP_SOUNDS)
                  build)
        response (post url sense-id (.toByteArray message))]
    (-> (post url sense-id (.toByteArray message))
      :body
      Messeji$Message/parseFrom)))

(defn- receive-message-request
  [sense-id acked-message-ids]
  (let [builder (Messeji$ReceiveMessageRequest/newBuilder)]
    (.setSenseId builder sense-id)
    (doseq [id acked-message-ids]
      (.addMessageReadId builder id))
    (.build builder)))

(defn receive-messages
  "Blocks waiting for new messages from server.

  Args:
    - host (String)
    - sense-id (String)
    - AES key (String)
    - acked-message-ids ([Int]) - message ids to acknowledge in the ReceiveMessageRequest

  Returns BatchMessage."
  [host sense-id key acked-message-ids]
  (let [url (str host "/receive")
        request-proto (receive-message-request sense-id acked-message-ids)
        signed-proto (sign-protobuf request-proto key)]
    (->> (post url sense-id signed-proto)
      :body
      bs/to-byte-array
      (drop (+ 16 32)) ;; drop injection vector and sig
      byte-array
      Messeji$BatchMessage/parseFrom)))

(defn- batch-messages
  [batch-message]
  (some->> batch-message
    .getMessageList
    seq))

(defn start-sense
  "Starts a new sense thread that receives messages and acknowledges read messages
  as they come in. The first 3 arguments are the same as receive-messages.

  callback-fn is a function that will be called on any new messages that arrive.
  The messages are passed as a seq of Message objects.

  Returns a Closeable object. Call .close() to shut down the polling thread."
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
                messages (batch-messages batch-message)]
            (when messages
              (callback-fn messages))
            (recur (map #(.getMessageId %) messages))))))
    (reify java.io.Closeable
      (close [this]
        (reset! running false)))))
