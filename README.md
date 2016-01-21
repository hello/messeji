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
