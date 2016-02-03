(ns com.hello.messeji.server-test
  (:require
    [aleph.http :as http]
    [clojure.test :refer :all]
    [com.hello.messeji.client :as client]
    [com.hello.messeji.protobuf :as pb]
    [com.hello.messeji.server :as server]
    [manifold.deferred :as deferred])
  (:import
    [com.hello.messeji.api
      Messeji$ReceiveMessageRequest]))

(def ^:private test-sense-id-key-pairs
  {"sense1" "F992550D1176B248191890FB1DBF5C4B"
   "sense2" "239EA46561BBFA83E58E643E5AF66A8E"})

;; Run for each test function
(use-fixtures :each
  (fn [test]
    (let [config-file (clojure.java.io/resource "config/dev.edn")]
      (with-open [server (server/-main config-file)]
        (test)))))

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
        (throws-400? (byte-array 10) {"X-Hello-Sense-Id" sense-id}))))


  #_(testing "Calling receive before there are any messages will time out."
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
      (Thread/sleep (* 1.5 timeout))
      (is (= @resp ::timed-out)))))
