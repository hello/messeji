(ns com.hello.messeji.db.test-in-mem
  (:require
    [clojure.test :refer :all]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.db.in-mem :as mem]
    [com.hello.messeji.protobuf :as pb])
  (:import
    [com.hello.messeji.api Messeji$Message]))

(defmacro at-time
  [t & body]
  `(with-redefs [mem/timestamp (constantly ~t)]
      ~@body))

(deftest test-create-message
  (let [max-age-millis 10000 ; 10 seconds
        message-store (mem/mk-message-store max-age-millis)
        sense-id "sense1"
        message (pb/message {:sender-id "test"
                             :order 100
                             :type (pb/message-type :sleep-sounds)
                             :sleep-sounds-command (pb/sleep-sounds-command {})})
        created (db/create-message message-store sense-id message)]
    (is (.hasMessageId created))
    (are [m] (= (m created) (m message))
      .getSenderId
      .getOrder
      .getType
      .getSleepSoundsCommand)))

(defn- nanos
  "Convert milliseconds to nanoseconds"
  [millis]
  (* millis 1000000))

(deftest test-unacked-messages
  (let [max-age-millis 10000 ; 10 seconds
        sense-id "sense1"
        message-store (mem/mk-message-store max-age-millis)
        message-1 (at-time
                    (nanos 0) ; First message is at time t0
                    (db/create-message
                      message-store
                      sense-id
                      (pb/message
                        {:sender-id "test"
                         :order 100
                         :type (pb/message-type :sleep-sounds)
                         :sleep-sounds-command (pb/sleep-sounds-command {})})))
         message-2 (at-time
                     (nanos 4000) ; 4 seconds in
                     (db/create-message
                       message-store
                       sense-id
                       (pb/message
                         {:sender-id "test"
                          :order 101
                          :type (pb/message-type :sleep-sounds)
                          :sleep-sounds-command (pb/sleep-sounds-command {})})))
         message-3 (at-time
                     (nanos 9000) ; 9 seconds in
                      (db/create-message
                        message-store
                        sense-id
                        (pb/message
                          {:sender-id "test"
                           :order 103
                           :type (pb/message-type :sleep-sounds)
                           :sleep-sounds-command (pb/sleep-sounds-command {})})))
         message-4 (at-time
                     (nanos 5000) ; 5 seconds in
                      (db/create-message
                        message-store
                        "sense2" ; different sense ID
                        (pb/message
                          {:sender-id "test"
                           :order 102
                           :type (pb/message-type :sleep-sounds)
                           :sleep-sounds-command (pb/sleep-sounds-command {})})))
        order-set (fn [messages] (->> messages (map #(.getOrder %)) set))]
    (testing "Only get messages for the correct sense id"
      (let [messages (at-time (nanos 9500) (doall (db/unacked-messages message-store sense-id)))]
        (is (= (order-set messages) #{100 101 103}))))
    (testing "Only get messages that aren't expired"
      (let [messages (at-time (nanos 11000) (doall (db/unacked-messages message-store sense-id)))]
        (is (= (order-set messages) #{101 103}))))
    (testing "Only unacked messages"
      (db/acknowledge message-store [(.getMessageId message-3)])
      (let [messages (at-time (nanos 9500) (doall (db/unacked-messages message-store sense-id)))]
        (is (= (order-set messages) #{100 101}))))))
