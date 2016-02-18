VERSION=$1
DEB="messeji_"$VERSION"_all.deb"
s3cmd put $DEB "s3://hello-deploy/packages/com/hello/messeji/"$VERSION"/messeji_"$VERSION"_amd64.deb"
