(ns com.hello.messeji.client
  (:require
    [aleph.http :as http]
    [byte-streams :as bs]
    [clojure.edn :as edn]
    [com.hello.messeji.config :as config]
    [com.hello.messeji.protobuf :as pb]
    [manifold.deferred :as deferred])
  (:import
    [com.hello.messeji.api Messeji$Message]
    [com.hello.messeji SignedMessage]
    [org.apache.commons.codec.binary Hex]))

(def ^:dynamic *connection-pool*
  (http/connection-pool {:connections-per-host 200}))

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

(defn- post-async
  [url sense-id body]
  (http/post
    url
    {:body body
     :headers {"X-Hello-Sense-Id" sense-id}
     :pool *connection-pool*}))

(def ^:private post
  (comp deref post-async))

(defn send-message
  "Send a message to the given sense-id,
  returning a Message object from the server."
  ([host sense-id message-map]
    (let [message-map (cond-> message-map
                        (not (:sender-id message-map)) (assoc :sender-id "clj-client")
                        (not (:order message-map)) (assoc :order (System/nanoTime)))
          url (str host "/send")
          message (pb/message message-map)
          response (post url sense-id (.toByteArray message))]
      (-> response
        :body
        pb/message)))
  ([host sense-id]
    (send-message
      host
      sense-id
      {:type (pb/message-type :stop-audio)
       :stop-audio (pb/stop-audio {:fade-out-duration-seconds 0})})))

(defn send-play-audio
  "Send a command to play audio."
  [host sense-id play-audio-map]
  (send-message
    host
    sense-id
    {:type (pb/message-type :play-audio)
     :play-audio (pb/play-audio play-audio-map)}))

(defn send-stop-audio
  "Send a command to stop audio."
  [host sense-id stop-audio-map]
  (send-message
    host
    sense-id
    {:type (pb/message-type :stop-audio)
     :stop-audio (pb/stop-audio stop-audio-map)}))

(defn get-status
  "Get message status from a message ID."
  [host message-id]
  (let [url (str host "/status/" message-id)
        response @(http/get url {})]
    (-> response
      :body
      pb/message-status)))

(defn- receive
  [host sense-id key acked-message-ids]
  (let [url (str host "/receive")
        request-proto (pb/receive-message-request
                        {:sense-id sense-id
                         :message-read-ids acked-message-ids})
        signed-proto (sign-protobuf request-proto key)]
    (deferred/chain
      (post-async url sense-id signed-proto)
      (fn [response]
        (->> response
          :body
          bs/to-byte-array
          (drop (+ 16 32)) ;; drop injection vector and sig
          byte-array
          pb/batch-message)))))

(defn receive-messages
  "Blocks waiting for new messages from server.

  Args:
    - host (String)
    - sense-id (String)
    - AES key (String)
    - acked-message-ids ([Int]) - message ids to acknowledge in the ReceiveMessageRequest

  Returns BatchMessage."
  [host sense-id key acked-message-ids]
  @(receive host sense-id key acked-message-ids))

(defn receive-messages-async
  "Same as receive-messages, but returns a manifold deferred."
  [host sense-id key acked-message-ids]
  (receive host sense-id key acked-message-ids))

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
