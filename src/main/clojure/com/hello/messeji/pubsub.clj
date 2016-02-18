(ns com.hello.messeji.pubsub
  (:require
    [com.hello.messeji.protobuf :as pb]
    [taoensso.carmine :as redis]))

(def ^:private channel "messages")

(defn publish
  "Publish a new message for the given sense-id."
  [conn-opts sense-id message]
  (redis/wcar conn-opts
    (redis/publish channel {:sense-id sense-id :message (.toByteArray message)})))

(defn listener
  [spec f]
  (redis/with-new-pubsub-listener spec
    {channel (fn [[_ _ {:keys [sense-id message]}]]
              (when (and sense-id message)
                (f sense-id (pb/message message))))}
    (redis/subscribe channel)))

(defn subscribe
  "Subscribe to the channel using the Redis spec.
  (f sense-id message) is executed when a new message is received.

  Returns a Closeable object."
  [spec f]
  (let [listener (listener spec f)]
    (reify java.io.Closeable
      (close [_]
        (redis/with-open-listener listener
          (redis/unsubscribe))
        (redis/close-listener listener)))))
