(defproject com.hello/messeji "0.1.0-SNAPSHOT"
  :description "Async messaging service for communicating with Sense"
  :url "https://github.com/hello/messeji"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [aleph "0.4.1-beta2"]
                 [compojure "1.4.0"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.10.49"]
                 [com.google.guava/guava "18.0"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [prismatic/schema "1.0.4"]]
  :plugins [[s3-wagon-private "1.2.0"]]
  :source-paths ["src" "src/main/clojure"]
  :java-source-paths ["src/main/java"]  ; Java source is stored separately.
  :resource-paths ["resources"]
  :main com.hello.messeji.server
  :aot [com.hello.messeji.server]
  :jvm-opts ["-server"]
  :repositories [["releases" {:url "s3p://hello-maven/release/"
                              :username :env/aws_access_key_id
                              :passphrase :env/aws_secret_key}]
                 ["snapshots" {:url "s3p://hello-maven/snapshot/"
                               :username :env/aws_access_key_id
                               :passphrase :env/aws_secret_key}]])
