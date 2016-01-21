# messeji

Async messaging service for communicating with sense.


## Installing/running
### Setup
Because we're using [s3-wagon](https://github.com/technomancy/s3-wagon-private) to access our internal maven repository, you need to let Leiningen know what your AWS credentials are. Do the followign (i.e. in your `~/.bash_profile`)

```bash
export LEIN_USERNAME=$AWS_ACCESS_KEY_ID
export LEIN_PASSPHRASE=$AWS_SECRET_KEY
```

### Running the server
```bash
lein run -m com.hello.messeji.server
```


## Protocol buffers
The protobuf can be found in our [proto repository](https://github.com/hello/proto/tree/master/messeji).

To compile the protobuf files (after they've changed):
```bash
protoc -I messeji/  --java_out=/path/to/messeji/src/main/java/ messeji/*
```
