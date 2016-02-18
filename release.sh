VERSION=$1
DEB="messeji_"$VERSION"_all.deb"
lein deploy packages com.hello/messeji $VERSION $DEB
