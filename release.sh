rm *.deb
VERSION_QUOTED=`lein release $1`
TEMP="${$VERSION_QUOTED%\"}"
VERSION="${TEMP#\"}"
DEB="messeji_"$VERSION"_all.deb"
lein deploy packages com.hello/messeji $VERSION $DEB
