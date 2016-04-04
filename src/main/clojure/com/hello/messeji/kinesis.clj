(ns com.hello.messeji.kinesis
  (:require
    [com.hello.messeji.protobuf :as pb])
  (:import
    [com.amazonaws.services.kinesis.producer
      KinesisProducer
      KinesisProducerConfiguration]
    [com.hello.messeji.api Logging$RequestLog]
    [com.hello.messeji.protobuf LoggingProtobuf]
    [java.nio ByteBuffer]))

(defn stream-producer
  [stream-name log-level]
  {:producer (KinesisProducer.
               (.. (KinesisProducerConfiguration.)
                (setRegion "us-east-1")
                (setLogLevel log-level)))
   :stream-name stream-name})

(defn put-request
  [{:keys [producer stream-name]} sense-id ^LoggingProtobuf request]
  (let [data (-> request pb/request-log .toByteArray ByteBuffer/wrap)]
    (.addUserRecord ^KinesisProducer producer stream-name sense-id data)))
