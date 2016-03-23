VERSION=$1

echo "Downloading $VERSION"
ssh messeji-dev "/home/ubuntu/build/download.sh $VERSION --force"

echo "Deploying $VERSION"
ssh messeji-dev "sudo /home/ubuntu/build/deploy.sh $VERSION"

echo "Tailing logs; feel free to kill this anytime."
ssh messeji-dev "tail -f /var/log/suripu-messeji/suripu-messeji.log"
