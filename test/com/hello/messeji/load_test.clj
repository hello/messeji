(ns com.hello.messeji.load-test
  (:require
    [aleph.http :as http]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [com.hello.messeji.client :as client]
    [manifold.deferred :as deferred]
    [manifold.stream :as s])
  (:import
    [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
    [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
    [com.amazonaws.services.dynamodbv2.model
      AttributeValue
      ScanRequest
      ScanResult]
    [com.hello.messeji.api Messeji$Message]))

(defn connect-senses
  "Given a seq of [sense-id, aes-key] pairs, connect senses to host and wait
  for new messages. Return a Closeable that will shutdown all Senses."
  [pool host sense-id-key-pairs callback-fn]
  (let [start-sense (fn [[sense-id aes-key]]
                      (client/start-sense host sense-id aes-key callback-fn
                        {:pool pool}))
        senses (mapv start-sense sense-id-key-pairs)]
    (reify java.io.Closeable
      (close [_]
        (doseq [sense senses]
          (.close sense))))))

(defn parse-file
  [filename]
  (with-open [reader (io/reader filename)]
    (mapv
      #(string/split % #" ")
      (line-seq reader))))

(defn callback
  [message-latency-stream]
  (fn [messages]
    (let [timestamp (System/nanoTime)]
      (doseq [m messages]
        (s/put! message-latency-stream
                (- timestamp (.getOrder ^Messeji$Message m)))))))

(defn process-stream
  [stream]
  (let [sorted-results (->> (s/stream->seq stream 1000)
                        (map #(/ % 1000000.))
                        sort)]
    (doseq [percentile [50 75 90 95 99]]
      (println (format "%sth percentile took %s ms"
                       percentile
                       (nth sorted-results (* (/ percentile 100) (count sorted-results))))))))

(defn run-test
  "The input file contains a list of senseid, aeskey pairs separated by a single space.
  This function connects all of the senses in the input file to the host, then
  sends a bunch of messages to randomly chosen connected senses. The latencies are
  measured for these messages, from the time they were sent to the time they
  were received by the 'sense'."
  [host filename & [limit]]
  (let [sense-id-key-pairs (set (parse-file filename))
        sense-id-key-pairs (if limit (take limit sense-id-key-pairs) sense-id-key-pairs)
        sense-ids (map first sense-id-key-pairs)
        message-latency-stream (s/stream)
        pool (http/connection-pool {:connections-per-host (count sense-ids)})]
    ;; Connect senses
    (with-open [senses (connect-senses pool host sense-id-key-pairs (callback message-latency-stream))]
      ;; Send a bunch of messages
      (dorun
        (pmap
          (fn [_]
            (client/send-message host (rand-nth sense-ids)))
          (range (* (count sense-ids) 5)))) ; Send 5 messages per sense at random
      ;; Print out the latency percentiles
      (process-stream message-latency-stream))))

(defn -main
  [host filename]
  (run-test host filename))

;; TODO currently this only returns the first page from the key store
(defn scan-key-store
  [limit]
  (let [table-name "key_store"
        client (AmazonDynamoDBClient. (DefaultAWSCredentialsProviderChain.))
        request (.. (ScanRequest. table-name)
                  (withLimit (int limit))
                  (withReturnConsumedCapacity "TOTAL"))
        result (.scan client request)]
    (println "Consumed capacity:" (.. result getConsumedCapacity getCapacityUnits))
    (mapv
      #(str
        (.. % (get "device_id") getS)
        " "
        (.. % (get "aes_key") getS))
      (.getItems result))))
