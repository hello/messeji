(ns com.hello.messeji.load-test
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [com.hello.messeji.client :as client]
    [manifold.deferred :as deferred]
    [manifold.stream :as s])
  (:import
    [com.hello.messeji.api Messeji$Message]))

(defn connect-senses
  "Given a seq of [sense-id, aes-key] pairs, connect senses to host and wait
  for new messages. Return a Closeable that will shutdown all Senses."
  [host sense-id-key-pairs callback-fn]
  (let [start-sense (fn [[sense-id aes-key]]
                      (client/start-sense host sense-id aes-key callback-fn))
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
  (loop []
    (let [time-deferred (s/take! stream)]
      (when (deferred/realized? time-deferred)
        (println "Message took" (/ @time-deferred 1000000.) "ms")
        (recur)))))

(defn run-test
  "The input file contains a list of senseid, aeskey pairs separated by a single space.
  This function connects all of the senses in the input file to the host, then
  sends a bunch of messages to randomly chosen connected senses. The latencies are
  measured for these messages, from the time they were sent to the time they
  were received by the 'sense'."
  [host filename]
  (let [sense-id-key-pairs (set (parse-file filename))
        _ (prn sense-id-key-pairs)
        sense-ids (map first sense-id-key-pairs)
        message-latency-stream (s/stream)]
    (with-open [senses (connect-senses host sense-id-key-pairs (callback message-latency-stream))]
      (dotimes [i (* (count sense-ids) 5)]
        (client/send-message host (rand-nth sense-ids)))
      (process-stream message-latency-stream))))

(defn -main
  [host filename]
  (run-test host filename))
