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

echo "Preparing new development version."
lein dev-version
