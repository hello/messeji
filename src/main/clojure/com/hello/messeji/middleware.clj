(ns com.hello.messeji.middleware
  (:require
    [byte-streams :as bs]
    [clojure.tools.logging :as log]
    [manifold.deferred :refer [let-flow]]
    [ring.middleware.content-type :refer [content-type-response]])
  (:import
    [com.google.protobuf
      InvalidProtocolBufferException
      Message]))

(def ^:private response-400
  {:status 400
   :body ""})

(defn wrap-protobuf-request
  "Catch parsing errors from deserializing protobuf requests."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch InvalidProtocolBufferException e
        response-400))))

(defn wrap-protobuf-response
  "If a protobuf message object is returned in the response body,
  convert it to a byte[]."
  [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (if (instance? Message body)
        (assoc response :body (.toByteArray body))
        response))))

(defn wrap-invalid-request
  "When an invalid request is thrown (see `throw-invalid-request`),
  it will be caught and turned into a 400 response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= ::invalid-request (-> e ex-data ::type))
          response-400
          (throw e))))))

(defn wrap-500
  "Return a generic 500 message to client instead of an exception trace."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (prn e)
        {:status 500
         :body ""}))))

(defn wrap-log-request
  "Log all request bodies."
  [handler]
  (fn [request]
    (log/debug request)
    (handler request)))

(defn wrap-content-type
  "`Deferred`-friendly version of ring's wrap-content-type."
  [handler]
  (fn [request]
    (let-flow [response (handler request)]
      (content-type-response response request))))

(defn throw-invalid-request
  "Throw an invalid request exception that will be caught by `wrap-invalid-request`
  and rethrown as a 400 error."
  ([reason]
    (log/info "Invalid request: " reason)
    (throw-invalid-request))
  ([]
    (throw (ex-info "Invalid request." {::type ::invalid-request}))))
