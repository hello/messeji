(ns com.hello.messeji.db.redis
  "Schema:

  latest_message_id - atomic int, maintains state of latest ID.

  message:<id> => Hash {
    :message Protobuf_byte[]
    :acknowledged? bool
    :sent? bool
    :timestamp millis-since-epoch
  }
  Messages expire in Redis after a number seconds to keep dataset small.

  sense:<sense-id>:messages => ZSet {(timestamp, message-id)}
  Every time messages are created or fetched, old message ids are deleted.
  This is to keep queries for messages per sense-id fast.
  "
  (:require
    [clojure.tools.logging :as log]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.protobuf :as pb]
    [taoensso.carmine :as redis])
  (:import
    [com.hello.messeji.api Messeji$Message]))

;; TODO sense "last seen"

(defn- expired?
  [max-age message-timestamp now]
  (> (- now message-timestamp) max-age))

(defn- message-state
  [{:keys [sent? acknowledged? timestamp]} max-age now]
  (cond
    acknowledged? :received
    sent? :sent
    (expired? max-age timestamp now) :expired
    :else :pending))

(defn- message-key
  [message-id]
  (format "message:%s" message-id))

(defn- r-latest-message-id
  []
  (redis/incr "latest_message_id"))

(defn- sense-messages-key
  [sense-id]
  (format "sense:%smessages" sense-id))

(defn- r-remove-old-messages
  "Removes message ids that are too old based on timestamp.
  Returns number of ids removed."
  [sense-id timestamp max-age-millis]
  (let [cutoff (- timestamp max-age-millis)]
    (redis/zremrangebyscore
      (sense-messages-key sense-id)
      "-inf" cutoff)))

(defn- r-add-message-for-sense
  [sense-id message-id timestamp]
  (redis/zadd (sense-messages-key sense-id) timestamp message-id))

(defn- str->long
  [^String x]
  (Long/parseLong x))

(defn- r-get-message-ids-for-sense
  "Return seq of [msg-id, timestamp] for the sense id between min and max timestamps.
  If min-ts and max-ts are not specified, gets all."
  ([sense-id]
    (r-get-message-ids-for-sense sense-id "-inf" "+inf"))
  ([sense-id min-ts max-ts]
    (redis/parse
      (fn [xs] (->> xs (map str->long) (partition 2)))
      (redis/zrangebyscore (sense-messages-key sense-id) min-ts max-ts "WITHSCORES"))))

(defn- micros->millis
  [micros]
  (long (/ micros 1000)))

(defn- seconds->millis
  [seconds]
  (* seconds 1000))

(defn- redis-time->milliseconds
  [[seconds micros]]
  (+ (-> seconds str->long seconds->millis)
     (-> micros str->long micros->millis)))

(defn- r-timestamp
  []
  (redis/parse redis-time->milliseconds (redis/time)))

(defn- r-message-set
  [k v msg-id]
  (redis/hset (message-key msg-id) k v))

(defn- parser-for-key
  [k]
  (case k
    :message pb/message
    :timestamp str->long
    identity))

(defn- message-parser
  [ks]
  (fn [vs]
    (if (some nil? vs) ; Some keys weren't found in Redis.
      nil
      (into {}
        (map
          (fn [k v] [k ((parser-for-key k) v)])
          ks vs)))))

(defn- r-message-get
  "Return a map containing the keys in ks for the message id."
  [id & ks]
  (redis/parse
    (message-parser ks)
    (apply redis/hmget (message-key id) ks)))

(defn- r-add-message
  [^Messeji$Message message timestamp]
  (redis/hmset (message-key (.getMessageId message))
    :message (.toByteArray message)
    :acknowledged? false
    :sent? false
    :timestamp timestamp))

(defn- into-map
  [ks vs]
  (into {} (map vector ks vs)))

(defn- update-messages
  "Updates the given key and value for the given messages. Expires all messages
  after delete-after-seconds."
  [conn-opts delete-after-seconds message-ids k v]
  (redis/wcar conn-opts
    (mapv (partial r-message-set k v) message-ids)
    (mapv #(redis/expire (message-key %) delete-after-seconds) message-ids)))

(defrecord RedisMessageStore
  [conn-opts max-age-millis delete-after-seconds]

  db/MessageStore
  (create-message
    [_ sense-id message]
    (let [[timestamp id] (redis/wcar conn-opts
                            (r-timestamp)
                            (r-latest-message-id))
          message-with-id (.. message toBuilder (setMessageId id) build)]
      (log/infof "fn=create-message sense-id=%s message-id=%s timestamp=%s"
        sense-id id timestamp)
      (redis/wcar conn-opts
        (redis/multi) ; Perform in a transaction
        (r-add-message message-with-id timestamp)
        (redis/expire (message-key id) delete-after-seconds)
        (r-add-message-for-sense sense-id id timestamp)
        (redis/exec)
        (r-remove-old-messages sense-id timestamp max-age-millis))
      message-with-id))

  (unacked-messages
    [_ sense-id]
    (let [[timestamp id-ts-pairs] (redis/wcar conn-opts
                                    (r-timestamp)
                                    (r-get-message-ids-for-sense sense-id))
          ts-cutoff (- timestamp max-age-millis)
          message-ids (->> id-ts-pairs
                        (filter #(> (second %) ts-cutoff)) ; Only keep if "new"
                        (map first))
          results (redis/wcar conn-opts :as-pipeline
                    (r-remove-old-messages sense-id timestamp max-age-millis)
                    (mapv #(r-message-get % :message :acknowledged?) message-ids))
          ;; Results are [remove-message-count msg-1 msg-2 ...]
          messages (rest results)]
      (->> messages
        (remove :acknowledged?)
        (map :message)
        (sort-by (juxt #(.getOrder ^Messeji$Message %)
                       #(.getMessageId ^Messeji$Message %))))))

  (get-status
    [_ message-id]
    (let [keys [:acknowledged? :sent? :timestamp]
          [timestamp message] (redis/wcar conn-opts
                                (r-timestamp)
                                (apply r-message-get message-id keys))]
      (when message
        (pb/message-status {:message-id message-id
                            :state (-> message
                                    (message-state max-age-millis timestamp)
                                    pb/message-status-state)}))))

  (mark-sent
    [_ message-ids]
    (update-messages conn-opts delete-after-seconds message-ids :sent? true))

  (acknowledge
    [_ message-ids]
    (update-messages conn-opts delete-after-seconds message-ids :acknowledged? true))

  java.io.Closeable
  (close [_] nil))


(defn mk-message-store
  ;; TODO this interface is weird with millis and seconds.
  [conn-opts max-message-age-millis delete-after-seconds]
  (->RedisMessageStore conn-opts max-message-age-millis delete-after-seconds))
