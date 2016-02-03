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
