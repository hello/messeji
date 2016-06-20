#!/bin/sh

if [ -z "$1" ]
  then
    echo "[ERROR] \xE2\x9A\xA0 Missing version number"
    exit 1
fi


VERSION=$1
ARGS=$2
s3cmd get s3://hello-deploy/configs/com/hello/messeji/$VERSION/messeji.staging.edn "/home/ubuntu/build/config/staging."$VERSION".edn" $ARGS
s3cmd get "s3://hello-maven/release/com/hello/messeji-standalone/$VERSION/messeji-standalone-"$VERSION".jar" /home/ubuntu/build/target/messeji-$VERSION-standalone.jar $ARGS
