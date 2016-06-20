#!/bin/sh

if [ -z "$1" ]
  then
    echo "[ERROR] \xE2\x9A\xA0 Missing version number"
    exit 1
fi


VERSION=$1
mv /usr/local/etc/hello/messeji.staging.yml /usr/local/etc/hello/messeji.staging.yml.old
ln -s "/home/ubuntu/build/config/staging."$VERSION".edn" /usr/local/etc/hello/messeji.staging.yml

mv /usr/local/bin/messeji.jar /usr/local/bin/messeji.jar.old
ln -s "/home/ubuntu/build/target/messeji-"$VERSION"-standalone.jar" /usr/local/bin/messeji.jar
restart messeji
