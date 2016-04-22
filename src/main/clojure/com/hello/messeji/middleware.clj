(ns com.hello.messeji.middleware
  (:require
    [byte-streams :as bs]
    [clojure.tools.logging :as log]
    [com.hello.messeji.metrics :as metrics]
    [manifold.deferred :as d :refer [let-flow]]
    [ring.middleware.content-type :refer [content-type-response]])
  (:import
    [com.google.protobuf
      InvalidProtocolBufferException
      Message]))

(def ^:private response-400
  {:status 400
   :body ""})

(defn- sense-id
  [request]
  (-> request :headers (get "X-Hello-Sense-Id")))

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

(defn wrap-exception
  "When an invalid request is thrown (see `throw-invalid-request`),
  it will be caught and turned into a 400 response.

  In case of exception, return a generic 500 message to client instead of an exception trace."
  [handler]
  (fn [request]
    ;; Need to use manifold chain/catch here because handler could return a deferred.
    (->  request
      (d/chain handler)
      (d/catch clojure.lang.ExceptionInfo
        (fn [e]
          (if (= ::invalid-request (-> e ex-data ::type))
            (do
              (log/errorf "error=invalid-request sense-id=%s uri=%s ip=%s"
                (sense-id request) (:uri request) (:remote-addr request))
              response-400)
            (throw e))))
      (d/catch
        (fn [e]
          (log/errorf "error=uncaught-exception sense-id=%s uri=%s ip=%s exception=%s"
            (sense-id request) (:uri request) (:remote-addr request) e)
          (metrics/mark "middleware.errors")
          {:status 500
           :body ""})))))

(defn wrap-log-request
  "Log all request bodies."
  [handler]
  (fn [request]
    (log/debug request)
    (handler request)))

(defn wrap-mark-request-meter
  "Mark a request meter metric."
  [handler]
  (fn [request]
    (metrics/mark "middleware.requests")
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
    (log/debug "Invalid request: " reason)
    (throw-invalid-request))
  ([]
    (metrics/mark "middleware.invalid-request")
    (throw (ex-info "Invalid request." {::type ::invalid-request}))))
