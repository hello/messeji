(ns com.hello.messeji.metrics
  (:refer-clojure :exclude [time])
  (:import
    [java.util.concurrent TimeUnit]
    [java.net InetSocketAddress]
    [com.codahale.metrics
      MetricFilter
      MetricRegistry]
    [com.codahale.metrics.graphite
      Graphite
      GraphiteReporter]))

(def ^:private registry-delayed
  (delay (MetricRegistry.)))

(defn registry
  "Return a singleton MetricRegistry instance."
  []
  @registry-delayed)

(defn start-graphite!
  "Starts and returns a GraphiteReporter.

  Remember to use with-open or call .close to shut down the reporting thread."
  [{:keys [host api-key port env period]}]
  (let [graphite (Graphite. (InetSocketAddress. host port))
        prefix (format "%s.%s.messeji" api-key env)
        reporter (.. (GraphiteReporter/forRegistry (registry))
                  (prefixedWith prefix)
                  (convertRatesTo TimeUnit/SECONDS)
                  (convertDurationsTo TimeUnit/MILLISECONDS)
                  (filter MetricFilter/ALL)
                  (build graphite))]
    (.start reporter period TimeUnit/SECONDS)
    reporter))

(defn meter
  "Get a Meter called `meter-name`."
  [meter-name]
  (.meter (registry) meter-name))

(defn timer
  "Get a Timer called `timer-name`."
  [timer-name]
  (.timer (registry) timer-name))

(defn mark
  "Mark the Meter called `meter-name`."
  ([meter-name]
    (.mark (meter meter-name)))
  ([meter-name count]
    (.mark (meter meter-name) count)))

(defmacro time
  "Gets Timer named `timer-name` and times the execution of `body`.

  (time \"my.timer\" (do-my-stuff))

  expands to:

  (let [timer (timer \"my.timer\")]
    (with-open [context (.time timer)]
      (do-my-stuff)))"
  [timer-name & body]
  `(let [timer# (timer ~timer-name)]
    (with-open [context# (.time timer#)]
      ~@body)))

(defmacro deftimed
  "Defines a new function named `symbol` that times the execution of `function`.

  (deftimed new-timed-fn my.prefix my-fn)

  expands to:

  (defn new-timed-fn
    [& args]
    (time \"my.prefix.my-fn\"
      (apply my-fn args)))"
  [symbol prefix function]
  (let [metric-name (str prefix "." function)]
    `(defn ~symbol
      [& args#]
      (time ~metric-name
        (apply ~function args#)))))
