(defproject com.hello/messeji "0.2.10"
  :description "Async messaging service for communicating with Sense"
  :url "https://github.com/hello/messeji"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [aleph "0.4.1-beta2"]
                 [compojure "1.4.0"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.10.49"]
                 [com.google.guava/guava "18.0"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
                 [io.dropwizard.metrics/metrics-core "3.1.0"]
                 [io.dropwizard.metrics/metrics-graphite "3.1.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 ;; sl4j dependencies are to bridge log4j and sl4j, which is
                 ;; used by dropwizard metrics *sigh*
                 [org.slf4j/slf4j-api "1.7.7"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [prismatic/schema "1.0.4"]
                 [com.taoensso/carmine "2.12.2"]]
  :plugins [[s3-wagon-private "1.2.0"]
            [lein-pprint "1.1.1"]
            [ystad/lein-deb "1.0.0"]]
  :source-paths ["src" "src/main/clojure"]
  :java-source-paths ["src/main/java"]  ; Java source is stored separately.
  :resource-paths ["resources"]
  :main com.hello.messeji.server
  :aot [com.hello.messeji.server]
  :jvm-opts ["-server", "-Dlogfile.path=./log"]
  :profiles {:dev {:plugins [[com.hello/lein-deploy-uberjar "0.1.0"]]}}
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)}
  :aliases {"package-deb" ["do" ["clean"] ["uberjar"] ["deb"]]
            "prep-release"
              ["do"
                ["clean"]
                ["test"]
                ["vcs" "assert-committed"]
                ["change" "version" "leiningen.release/bump-version" "release" ":patch"]]
            "bump-version" ["change" "version" "leiningen.release/bump-version"]}
  :release-tasks [["prep-release"]
                  ["pprint" ":version"]
                  ["package-deb"]
                  ["dev-version"]]
  :deploy-branches ["master"]
  :deb {
    :filesets [{:file "target/messeji-*-standalone.jar"
                :fullpath "/opt/hello/messeji.jar"}
               {:file "resources/config/prod.edn"
                :fullpath "/etc/hello/messeji.prod.edn"}
               {:file "init/messeji.conf"
                :fullpath "/etc/init/messeji.conf"}]}
  :repositories [["releases" {:url "s3p://hello-maven/release/"
                              :username :env/aws_access_key_id
                              :passphrase :env/aws_secret_key
                              :sign-releases false}]
                 ["snapshots" {:url "s3p://hello-maven/snapshot/"
                               :username :env/aws_access_key_id
                               :passphrase :env/aws_secret_key}]
                 ["packages" {:url "s3p://hello-deploy/packages/"
                              :username :env/aws_access_key_id
                              :passphrase :env/aws_secret_key}]])
