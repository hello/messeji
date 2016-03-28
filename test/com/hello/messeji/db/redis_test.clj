(ns com.hello.messeji.db.redis-test
  (:require
    [clojure.test :refer :all]
    [com.hello.messeji.config :as conf]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.protobuf :as pb]
    [com.hello.messeji.db.redis :as redis]
    [taoensso.carmine :as car])
  (:import
    [com.hello.messeji.api Messeji$Message]))

(defmacro at-time
  "Redef the timestamp parser to always return t while executing body."
  [t & body]
  `(with-redefs [redis/redis-time->milliseconds (constantly ~t)]
      ~@body))

(defn tear-down
  [config]
  (car/wcar {:spec (get-in config [:redis :spec])}
    (car/flushdb)))

(defn- get-message-store
  [config-map]
  (tear-down config-map)
  (redis/mk-message-store
    {:spec (get-in config-map [:redis :spec])}
    (:max-message-age-millis config-map)
    (get-in config-map [:redis :delete-after-seconds])))

(defn- get-config
  []
  (let [dev-config-file (clojure.java.io/resource "config/dev.edn")]
    (-> dev-config-file
      conf/read
      (update-in [:redis :spec :db] inc)  ; Don't use the normal dev DB.
      (assoc :max-message-age-millis 10000))))

(defmacro with-redis
  [sym & body]
  `(let [config# (get-config)
         ~sym (get-message-store config#)]
      (try ~@body
        (finally
          (tear-down config#)))))

(deftest ^:integration test-create-message
  (with-redis message-store
    (let [sense-id "sense1"
          message (pb/message {:sender-id "test"
                               :order 100
                               :type (pb/message-type :stop-audio)
                               :stop-audio (pb/stop-audio {:fade-out-duration-seconds 1})})
          created (db/create-message message-store sense-id message)]
      (is (.hasMessageId created))
      (are [m] (= (m created) (m message))
        .getSenderId
        .getOrder
        .getType
        .getStopAudio))))

(deftest ^:integration test-unacked-messages
  (with-redis message-store
    (let [sense-id "sense1"
          stop-audio (pb/stop-audio {:fade-out-duration-seconds 1})
          message-1 (at-time
                      0 ; First message is at time t0
                      (db/create-message
                        message-store
                        sense-id
                        (pb/message
                          {:sender-id "test"
                           :order 100
                           :type (pb/message-type :stop-audio)
                           :stop-audio stop-audio})))
           message-2 (at-time
                       4000 ; 4 seconds in
                       (db/create-message
                         message-store
                         sense-id
                         (pb/message
                           {:sender-id "test"
                            :order 101
                            :type (pb/message-type :stop-audio)
                            :stop-audio stop-audio})))
           message-3 (at-time
                       9000 ; 9 seconds in
                        (db/create-message
                          message-store
                          sense-id
                          (pb/message
                            {:sender-id "test"
                             :order 103
                             :type (pb/message-type :stop-audio)
                             :stop-audio stop-audio})))
           message-4 (at-time
                       5000 ; 5 seconds in
                        (db/create-message
                          message-store
                          "sense2" ; different sense ID
                          (pb/message
                            {:sender-id "test"
                             :order 102
                             :type (pb/message-type :stop-audio)
                             :stop-audio stop-audio})))
          order-set (fn [messages] (->> messages (map #(.getOrder %)) set))]
      (testing "Only get messages for the correct sense id"
        (let [messages (at-time 9500 (doall (db/unacked-messages message-store sense-id)))]
          (is (= (order-set messages) #{100 101 103}))))
      (testing "Only get messages that aren't expired"
        (let [messages (at-time 11000 (doall (db/unacked-messages message-store sense-id)))]
          (is (= (order-set messages) #{101 103}))))
      (testing "Only unacked messages"
        (db/acknowledge message-store [(.getMessageId message-3)])
        (let [messages (at-time 11000 (doall (db/unacked-messages message-store sense-id)))]
          (is (= (order-set messages) #{101})))))))

(deftest ^:integration test-get-status
  (with-redis message-store
    (let [sense-id "sense1"
          message (pb/message {:sender-id "test"
                               :order 100
                               :type (pb/message-type :play-audio)})
          ;; Insert message at time 0 and get new message ID.
          insert-msg #(at-time 0
                        (.getMessageId
                          (db/create-message message-store sense-id message)))
          get-status (fn [millis id] (at-time millis (db/get-status message-store id)))
          equal-state? #(= (.getState %1) (pb/message-status-state %2))]
      (testing "Initial state is pending."
        (let [message-id (insert-msg)
              status (get-status 5000 message-id)]
          (is (equal-state? status :pending))))
      (testing "Expired"
        (let [message-id (insert-msg)
              status (get-status 11000 message-id)]
          (is (equal-state? status :expired))))
      (testing "Sent"
        (let [message-id (insert-msg)]
          (db/mark-sent message-store [message-id])
          (testing "Sent takes precedence over pending"
            (is (equal-state? (get-status 3000 message-id) :sent)))
          (testing "Sent takes precedence over expired."
            (is (equal-state? (get-status 12000 message-id) :sent)))))
      (testing "Received"
        (let [message-id (insert-msg)]
          (db/acknowledge message-store [message-id])
          (testing "Received takes precedence over pending."
            (is (equal-state? (get-status 3000 message-id) :received)))
          (testing "Received takes precedence over sent."
            (db/mark-sent message-store [message-id])
            (is (equal-state? (get-status 3000 message-id) :received)))
          (testing "Received takes precedence over all."
            (is (equal-state? (get-status 13000 message-id) :received))))))))

(deftest ^:integration test-acked-message-still-gets-deleted
  (let [sense-id "sense1"
        delete-after-seconds 1 ; Make this really short for testing
        config (assoc-in (get-config) [:redis :delete-after-seconds] delete-after-seconds)
        message-store (get-message-store config)
        message (pb/message {:sender-id "test"
                             :order 100
                             :type (pb/message-type :play-audio)})
        message-with-id (db/create-message message-store sense-id message)
        id (.getMessageId message-with-id)]
    (Thread/sleep (* delete-after-seconds 2 1000)) ; Wait for message to be deleted
    (is
      (nil? (db/get-status message-store id))
      "Message should be deleted after waiting for delete-after-seconds")
    (db/acknowledge message-store [id]) ; Should do nothing since message is gone.
    (is
      (nil? (db/get-status message-store id))
      "Message should still be deleted even after acknowledging.")
    (tear-down config)))
