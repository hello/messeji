(ns com.hello.messeji.db.key-store-ddb
  (:require
    [com.hello.messeji.db :as db])
  (:import
    [com.amazonaws.services.dynamodbv2.model
      AttributeValue
      GetItemRequest
      ResourceNotFoundException]
    [org.apache.commons.codec.binary Hex]))

(def ^:private default-aes-key
  (.getBytes "1234567891234567"))

(defn- attribute-value
  [s]
  (.withS (AttributeValue.) s))

(defn- ddb-get-item
  "Returns a GetItemResult if successful, or nil."
  [ddb-client get-item-request]
  (try
    (.getItem ddb-client get-item-request)
    (catch ResourceNotFoundException rnfe)))

(defn- decode-key
  [key-str]
  (-> key-str .toCharArray Hex/decodeHex))

(defrecord KeyStoreDynamoDB [table-name ddb-client]
  db/KeyStore
  (get-key
    [_ sense-id]
    (let [get-item-request (.. (GetItemRequest.)
                              (withTableName table-name)
                              (withKey {"device_id" (attribute-value sense-id)}))
          result (ddb-get-item ddb-client get-item-request)]
      (some-> result .getItem (.get "aes_key") .getS decode-key)))

  java.io.Closeable
  (close
    [this]
    (.shutdown ddb-client)
    this))

(defn key-store
  "Create and return a KeyStore."
  [table-name ddb-client]
  (->KeyStoreDynamoDB table-name ddb-client))
