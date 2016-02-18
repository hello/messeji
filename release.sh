lein prep-release
lein package-deb
VERSION_QUOTED=`lein pprint :version`
TEMP="${VERSION_QUOTED%\"}"
VERSION="${TEMP#\"}"
echo "New version " $VERSION

DEB="messeji_"$VERSION"_all.deb"
echo "Uploading deb package to S3: " $DEB
s3cmd put $DEB "s3://hello-deploy/packages/com/hello/messeji/"$VERSION"/messeji_"$VERSION"_amd64.deb"
rm *.deb

echo "Deploying uberjar to maven repo"
lein deploy-uberjar

echo "Uploading configs"
s3cmd put resources/config/prod.edn s3://hello-deploy/configs/com/hello/messeji/$VERSION/messeji.prod.edn
s3cmd put resources/config/staging.edn s3://hello-deploy/configs/com/hello/messeji/$VERSION/messeji.staging.edn

echo "Preparing new development version."
lein dev-version
