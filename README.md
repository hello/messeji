# messeji

Async messaging service for communicating with sense.


## Installing/running
### Setup
Because we're using [s3-wagon](https://github.com/technomancy/s3-wagon-private) to access our internal maven repository, you need to let Leiningen know what your AWS credentials are. Do the following (i.e. in your `~/.bash_profile`)

```bash
export LEIN_USERNAME=$AWS_ACCESS_KEY_ID
export LEIN_PASSPHRASE=$AWS_SECRET_KEY
```

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
