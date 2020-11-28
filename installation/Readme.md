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
**(alternative: https://docs.datastax.com/en/jdk-install/doc/jdk-install/installOracleJdkDeb.html)**
    * sudo add-apt-repository ppa:webupd8team/java
    * sudo apt update && sudo apt install oracle-java8-installer
    * sudo apt install oracle-java8-set-default
* Install mongodb db
    * use official documentation
    * change default port in /etc/mongod.conf to 26459
    * sudo systemctl start mongod
    * sudo systemctl status mongod
    * check version
        ```bash
        mongo --port 26459 --eval "db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )"
        ```    
    * Enable Replica Set - see #update-mongodb.md
    * copy backup from working system - see /mongobackups/*
    
    
* Create folder for backend. sudo mkdir -p /opt/bitplay/bitmex-okcoin
* Create service. cp {git:backend}/installation/bitplay2.service /etc/systemd/system/
* do gradle deploy -i
* Register your api keys:
    * cp {git:backend}/installation/application.properties /opt/bitplay/bitmex-okcoin/
    * set your own api keys
    * sudo systemctl restart bitplay2
* request http://hostname:4031/borders/create-default


