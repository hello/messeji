VERSION=$1
DEB="messeji_"$VERSION"_all.deb"
NEW_DEB="messeji_"$VERSION"_amd64.deb"
mv $DEB $NEW_DEB
lein deploy packages com.hello/messeji $VERSION $NEW_DEB
