# messeji

Async messaging service for communicating with sense.


## Installing/running

### Running the server
```bash
lein run -m com.hello.messeji.server resources/config/dev.edn
```

This will use `dev.edn` as a configuration file.

### Using local configuration files
If you want to modify part of the local configuration but not change the repo,
use a `*.local.edn` file.

```bash
lein run -m com.hello.messeji.server resources/config/dev.edn dev.local.edn
```

`dev.local.edn` might not exist (it's not in the repository), so you'll need to create
your own to use this feature. This allows you to merge any changes from your local file
over the original (the second argument clobbers any config params in the first arg.)

For example, if `dev.edn` is

```clojure
{:a {:x 1, :y 2}
 :b "hello"}
```

and `dev.local.edn` is

```clojure
{:a {:x 5}}
```

then the resulting config will be
```clojure
{:a {:x 5, :y 2}
 :b "hello"}
```


## Protocol buffers
The protobuf can be found in our [proto repository](https://github.com/hello/proto/tree/master/messeji).

To compile the protobuf files (after they've changed):
```bash
protoc -I messeji/  --java_out=/path/to/messeji/src/main/java/ messeji/*
```


## Client
First, some setup: Require the client and define your sense and its key (to receive messages).
```clojure
(require '[com.hello.messeji.client :as client])

(def sense-id "sense1") ; Replace this with a real sense id

(def aes-key "my-key") ; Again, replace with a real key
```

We also need to define a host to connect to. For our local server, we can use the handy `localhost` function.

```clojure
(def host (client/localhost)) ; Or any string, e.g. "http://myhost:10000"
```

Now let's start a sense. To do this, we start a new thread, and pass in a callback function to execute whenever
we receive new messages. In this example, let's just print out the IDs of messages we receive.
```clojure
(defn print-message-ids
  [messages]
  (->> messages (map #(.getMessageId %)) prn))

(def sense (client/start-sense host sense-id aes-key print-message-ids))
```

Now `sense` is an object that will poll our server for new messages. Let's send a message:
```clojure
(client/send-message host sense-id)
```

In addition to seeing the returned message from the server, you should see the message-id printed out to the REPL from our sense thread.

To shut down the sense thread, just close it:
```clojure
(.close sense)
```

Because it's Closeable, you could also wrap it in `with-open` etc.
