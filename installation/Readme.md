## New server installation

* install ubuntu 16.04.

* Add your user. Change default ssh port
* install nginx. Change default port to 3971
* Add frontend deploy:
    * set your host as a new one in gradle.build
    * set your credentials in {userDir}/.gradle/gradle.properties
    * mkdir ~/bitplay-gui
    * mkdir /var/www/bitplay-gui && chown $USER:users /var/www/bitplay-gui
    * gradle deployXXX -i
    * Check webserver
* Install java 8
    * sudo add-apt-repository ppa:webupd8team/java
    * sudo apt update && sudo apt install oracle-java8-installer
    * sudo apt install oracle-java8-set-default
* Install mongodb db version v3.2.17
    * sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv EA312927
    * echo "deb http://repo.mongodb.org/apt/ubuntu xenial/mongodb-org/3.2 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-3.2.list
    * sudo apt-get update
    * sudo apt-get install -y mongodb-org
    * change default port in /etc/mongod.conf to 26459
    * sudo systemctl start mongod
    * sudo systemctl status mongod
* Create folder for backend. sudo mkdir -p /opt/bitplay/bitmex-okcoin
* Create service. cp {git:backend}/installation/bitplay2.service /etc/systemd/system/
* do gradle deploy -i
* Register your api keys:
    * cp {git:backend}/installation/application.properties /opt/bitplay/bitmex-okcoin/
    * set your own api keys
    * sudo systemctl restart bitplay2
* request http://hostname:4031/borders/create-default


