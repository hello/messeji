(ns com.hello.messeji.server-test
  (:require
    [clojure.test :refer :all]
    [com.hello.messeji.client :as client]
    [com.hello.messeji.server :as server]))

;; Run for each test function
(use-fixtures :each
  (fn [test]
    (let [config-file (clojure.java.io/resource "config/dev.edn")]
      (with-open [server (server/-main config-file)]
        (test)))))

(deftest ^:integration test-whatever
  (prn "lol"))
