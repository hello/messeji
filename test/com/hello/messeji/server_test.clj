(ns com.hello.messeji.server-test
  (:require
    [aleph.http :as http]
    [clojure.test :refer :all]
    [com.hello.messeji.client :as client]
    [com.hello.messeji.db :as db]
    [com.hello.messeji.db.key-store-ddb :as ksddb]
    [com.hello.messeji.protobuf :as pb]
    [com.hello.messeji.server :as server]
    [manifold.deferred :as deferred])
  (:import
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest]
    [org.apache.commons.codec.binary Hex]))

(def ^:private test-sense-id-key-pairs
  {"sense-id-1" "31323334353637383931323334353637"
   "sense-id-2" "31323334353637383931323334353638"})

(def mock-key-store
  (reify
    db/KeyStore
    (get-key
      [_ sense-id]
      (some-> sense-id test-sense-id-key-pairs .toCharArray Hex/decodeHex))

    java.io.Closeable
    (close [_] nil)))

;; Run for each test function
(use-fixtures :each
  (fn [test]
    (let [config-file (clojure.java.io/resource "config/dev.edn")]
      (with-redefs [ksddb/key-store (constantly mock-key-store)]
        (with-open [server (server/-main config-file)]
          (test))))))

(deftest ^:integration test-send
  (let [message (client/send-message (client/localhost) "sense1")]
    (is (.hasMessageId message) "Message from server should have ID.")
    (is (-> (client/get-status (client/localhost) (.getMessageId message))
          .getState
          (= (pb/message-status-state :pending))))))

(deftest ^:integration test-receive
  (testing "Receive request validation"
    (let [url (str (client/localhost) "/receive")
          [sense-id aes-key] (first test-sense-id-key-pairs)
          mk-body #(-> {:sense-id %
                        :message-read-ids []}
                     pb/receive-message-request
                     (client/sign-protobuf aes-key))
          body (mk-body sense-id)
          body (-> {:sense-id sense-id
                    :message-read-ids []}
                  pb/receive-message-request
                  (client/sign-protobuf aes-key))
          throws-400? #(is
                        (thrown-with-msg?
                          clojure.lang.ExceptionInfo
                          #"400"
                          @(http/post
                            url
                            {:body %1
                             :headers %2})))]
      (testing "Missing sense-id in header will throw 400."
        (throws-400? body {}))
      (testing "Sense-id in header doesn't match sense id in protobuf."
        (throws-400? body {"X-Hello-Sense-Id" "some-other-sense-id"}))
      (testing "Invalid protobuf body"
        (throws-400? (byte-array 10) {"X-Hello-Sense-Id" sense-id}))
      (testing "Using a sense-id with no associated key."
        (let [fake-sense-id "fake-sense-id"]
          (throws-400? (mk-body "fake-sense-id") {"X-Hello-Sense-Id" sense-id})))))

  (testing "Calling receive before there are any messages will time out."
    (let [timeout 1000
          [sense-id aes-key] (first test-sense-id-key-pairs)
          resp (deferred/timeout!
                (client/receive-messages-async
                  (client/localhost)
                  sense-id
                  aes-key
                  [])
                timeout
                ::timed-out)]
      (is (= @resp ::timed-out)))))

(deftest ^:integration test-receive-multiple-messages
  (let [[sense-id-1 key-1] (first test-sense-id-key-pairs)
        message-1 (client/send-message (client/localhost) sense-id-1)
        message-2 (client/send-message (client/localhost) sense-id-1)
        message-3 (client/send-message (client/localhost) (key (second test-sense-id-key-pairs)))
        batch-message (client/receive-messages (client/localhost) sense-id-1 key-1 [])]
    (is (= #{(.getMessageId message-1) (.getMessageId message-2)}
           (->> batch-message .getMessageList (map #(.getMessageId %)) set))
        "Both messages for this sense-id should be retrieved, but not the other message.")))

(deftest ^:integration test-receive-new-messages-while-connected
  (let [[sense-id-1 key-1] (first test-sense-id-key-pairs)
        batch-message-deferred (client/receive-messages-async
                                (client/localhost) sense-id-1 key-1 [])
        _ (Thread/sleep 1000)
        message (client/send-message (client/localhost) sense-id-1)]
    (is (= (.getMessageId message)
           (-> batch-message-deferred deref (.getMessage 0) .getMessageId))
        "Message returned after being sent by while connected.")))

(deftest ^:integration test-receive-only-unacked-messages
  (let [[sense-id-1 key-1] (first test-sense-id-key-pairs)
        message-1 (client/send-message (client/localhost) sense-id-1)
        batch-1 (client/receive-messages (client/localhost) sense-id-1 key-1 [])
        batch-2 (client/receive-messages (client/localhost) sense-id-1 key-1 [])
        batch-3-deferred (client/receive-messages-async
                          (client/localhost) sense-id-1 key-1
                          [(.getMessageId message-1)])
        message-2 (client/send-message (client/localhost) sense-id-1)]
    (is (= #{(.getMessageId message-1)}
           (->> batch-1 .getMessageList (map #(.getMessageId %)) set)
           (->> batch-2 .getMessageList (map #(.getMessageId %)) set))
        "Messages should be returned until they are acked.")
    (is (= #{(.getMessageId message-2)}
           (->> batch-3-deferred deref .getMessageList (map #(.getMessageId %)) set))
        "Only include unacked messages in response.")))

(deftest ^:integration test-get-status
  (let [[sense-id-1 key-1] (first test-sense-id-key-pairs)
        message-1 (client/send-message (client/localhost) sense-id-1)
        message-2 (client/send-message (client/localhost) sense-id-1)
        message-3 (client/send-message (client/localhost) "nonsense")
        id-1 (.getMessageId message-1)
        id-2 (.getMessageId message-2)
        id-3 (.getMessageId message-3)
        get-state #(-> (client/get-status (client/localhost) %) .getState)
        ack #(client/receive-messages (client/localhost) sense-id-1 key-1 %)]
    (is (= (pb/message-status-state :pending)
           (get-state id-1)
           (get-state id-2)
           (get-state id-3))
        "All messages are initially pending.")
    (testing "sent"
      (ack [])
      (is (= (pb/message-status-state :sent)
             (get-state id-1)
             (get-state id-2))
          "Sent messages are correctly marked as sent.")
      (is (= (pb/message-status-state :pending)
             (get-state id-3))
          "Unsent message is still pending."))
    (testing "received"
      (ack [id-2])
      (are [id state] (= (pb/message-status-state state) (get-state id))
        id-1 :sent
        id-2 :received
        id-3 :pending))))

(deftest ^:integration test-get-status-invalid-id
  (testing "id is invalid"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"400"
          (client/get-status (client/localhost) "not-valid"))))
  (testing "id is valid but not found"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"404"
          (client/get-status (client/localhost) 1337)))))
