(ns com.hello.messeji.middleware
  (:require
    [byte-streams :as bs])
  (:import
    [com.google.protobuf
      InvalidProtocolBufferException
      Message]))

(def ^:private response-400
  {:status 400
   :body ""})

(defn wrap-protobuf-request
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch InvalidProtocolBufferException e
        response-400))))

(defn wrap-protobuf-response
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (instance? Message (:body response))
        (update-in response [:body] #(-> % .toByteArray bs/to-input-stream))
        response))))

(defn wrap-invalid-request
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= ::invalid-request (-> e ex-data ::type))
          response-400
          (throw e))))))

(defn wrap-500
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :body ""}))))

(defn throw-invalid-request
  []
  (throw (ex-info "Invalid request." {::type ::invalid-request})))
