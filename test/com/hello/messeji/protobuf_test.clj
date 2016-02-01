(ns com.hello.messeji.protobuf-test
  (:require
    [byte-streams :as bs]
    [clojure.test :refer :all]
    [com.hello.messeji.protobuf :as pb])
  (:import
    [com.hello.messeji.api
      Messeji$BatchMessage
      Messeji$Message
      Messeji$Message$Type
      Messeji$MessageStatus
      Messeji$MessageStatus$State
      Messeji$ReceiveMessageRequest
      SleepSounds$SleepSoundsCommand]))

(deftest test-message
  (testing "Do not require optional fields"
    (let [message-type (pb/message-type :sleep-sounds)
          order 100
          message (pb/message {:type message-type, :order order})]
      (are [method y] (= (method message) y)
        .getType message-type
        .getOrder order)
      (are [method] (not (method message))
        .hasSenderId .hasMessageId .hasSleepSoundsCommand)))
  (testing "Include optional fields"
    (let [message-type (pb/message-type :sleep-sounds)
          sender-id "sender1"
          order 100
          message-id 1
          command (pb/sleep-sounds-command {})
          message (pb/message {:sender-id sender-id, :order order,
                               :message-id message-id, :type message-type,
                               :sleep-sounds-command command})]
      (are [method y] (= (method message) y)
        .getType message-type
        .getSenderId sender-id
        .getOrder order
        .getMessageId message-id
        .getSleepSoundsCommand command))))

(deftest test-batch-message
  (testing "empty messages"
    (let [batch-message (pb/batch-message {:messages []})]
      (is (zero? (.getMessageCount batch-message)))))
  (testing "Multiple messages"
    (let [message-type (pb/message-type :sleep-sounds)
          message-1 (pb/message {:type message-type, :order 1})
          message-2 (pb/message {:type message-type, :order 2})
          batch-message (pb/batch-message {:messages [message-1 message-2]})]
      (is (= 2 (.getMessageCount batch-message)))
      (are [idx m] (.equals (.getMessage batch-message idx) m)
        0 message-1
        1 message-2))))

(deftest test-message-status
  (are [msg-id state] (let [status (pb/message-status {:message-id msg-id :state state})]
                        (and (= msg-id (.getMessageId status))
                             (= state (.getState status))))
    1 (pb/message-status-state :pending)
    2 (pb/message-status-state :sent)
    3 (pb/message-status-state :received)
    4 (pb/message-status-state :expired)))

(deftest test-receive-message-request
  (let [sense-id "sense1"
        id-1 1
        id-2 2
        rmr (pb/receive-message-request {:sense-id sense-id
                                         :message-read-ids [id-1 id-2]})]
    (is (= 2 (.getMessageReadIdCount rmr)))
    (are [idx id] (= (.getMessageReadId rmr idx) id)
      0 id-1
      1 id-2)))

(deftest test-parsing
  (let [message (pb/message {:type (pb/message-type :sleep-sounds)
                             :order 100
                             :sender-id "sender"
                             :message-id 1})
        batch-message (pb/batch-message {:messages [message]})
        message-status (pb/message-status {:message-id 1
                                           :state (pb/message-status-state :pending)})
        receive-message-request (pb/receive-message-request
                                  {:sense-id "sense1"
                                   :message-read-ids [1]})
        sleep-sounds-command (pb/sleep-sounds-command {})]
    (testing "byte[]"
      (are [obj f] (-> obj .toByteArray f (.equals obj))
        message pb/message
        batch-message pb/batch-message
        message-status pb/message-status
        receive-message-request pb/receive-message-request
        sleep-sounds-command pb/sleep-sounds-command))
    (testing "InputStream"
      (are [obj f] (-> obj .toByteArray bs/to-input-stream f (.equals obj))
        message pb/message
        batch-message pb/batch-message
        message-status pb/message-status
        receive-message-request pb/receive-message-request
        sleep-sounds-command pb/sleep-sounds-command))))
