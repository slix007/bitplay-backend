Current in use version 3.2.22, Standalone.
The recomendation to use Replica Set v4.0

Update 1 server with current instruction without troubles: 15 min.


# Upgrade mongodb 3.2 to 4.0
#### 1) 3.2 -> 3.4

```bash
sudo systemctl stop bitplay2 && \
wget https://repo.mongodb.org/apt/ubuntu/dists/xenial/mongodb-org/3.4/multiverse/binary-amd64/mongodb-org-server_3.4.22_amd64.deb && \
sudo systemctl stop mongod && \
sudo dpkg -i mongodb-org-server_3.4.22_amd64.deb && \
sudo systemctl start mongod

```
Go to mongo console and set new compatibility version
```bash
$ mongo --port 26459
# check 
> db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )
# set new
> db.adminCommand({setFeatureCompatibilityVersion: "3.4"})

### or
mongo --port 26459 --eval "db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )"
mongo --port 26459 --eval 'db.adminCommand({setFeatureCompatibilityVersion: "3.4"})'
```
#### 2) 3.4 -> 3.6
NOTE: Don't change /etc/mongo.conf!
Restore settings in /etc/mongo.conf. 
```bash
  port: 26459
  bindIp: 127.0.0.1
```

```
sudo systemctl start bitplay2
# do check
sudo systemctl stop bitplay2

wget https://repo.mongodb.org/apt/ubuntu/dists/xenial/mongodb-org/3.6/multiverse/binary-amd64/mongodb-org-server_3.6.13_amd64.deb && \
sudo systemctl stop mongod && \
sudo dpkg -i mongodb-org-server_3.6.13_amd64.deb

# If it asks to replace the file at /etc/mongod.conf just say no to keep your settings as they are.
sudo systemctl start mongod

```
Go to mongo console and set new compatibility version
```bash
mongo --port 26459 --eval "db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )"
mongo --port 26459 --eval 'db.adminCommand({setFeatureCompatibilityVersion: "3.6"})'
```


### 3) 3.6 -> 4.0 from apt repository

```
wget https://repo.mongodb.org/apt/ubuntu/dists/xenial/mongodb-org/4.0/multiverse/binary-amd64/mongodb-org-server_4.0.12_amd64.deb && \
sudo systemctl stop mongod && \
sudo apt install libcurl3 && \
sudo dpkg -i mongodb-org-server_4.0.12_amd64.deb

# If it asks to replace the file at /etc/mongod.conf just say no to keep your settings as they are.
sudo systemctl start mongod
```
Go to mongo console and set new compatibility version

```
```bash
mongo --port 26459 --eval "db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )"
mongo --port 26459 --eval 'db.adminCommand({setFeatureCompatibilityVersion: "4.0"})'
```

### 3) 4.0 -> 4.2 from apt repository
`sudo systemctl stop mongod`

Check ubuntu version `lsb_release -a`
Use proper repositorie from: https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/
```bash
wget -qO - https://www.mongodb.org/static/pgp/server-4.2.asc | sudo apt-key add -
echo "deb ....."
lsb_release -a
ls /etc/apt/sources.list.d/
sudo rm /etc/apt/sources.list.d/mongodb-org-3.2.list
# See the instruction above... The 16.04 version
sudo echo "deb [ arch=amd64 ] https://repo.mongodb.org/apt/ubuntu xenial/mongodb-org/4.2 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-4.2.list

sudo apt-get update && \
sudo apt-get install -y mongodb-org && \
sudo apt dist-upgrade

apt search mongo | grep mongo | grep installed
``` 
Go to mongo console and set new compatibility version
```bash
sudo systemctl start mongod
mongo --port 26459 --eval "db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )"
mongo --port 26459 --eval 'db.adminCommand({setFeatureCompatibilityVersion: "4.2"})'
```
### 3) 4.4 -> 5.0 from apt repository
https://docs.mongodb.com/manual/release-notes/5.0-upgrade-replica-set/



Trobleshooting:
If you get something like `mongodb-org-mongos : Depends: libssl1.0.0 (>= 1.0.1) but it is not   installable`
Try to check ubuntu version `lsb_release -a`
```bash
sudo apt purge mongod* 
sudo apt purge mongodb* 
sudo apt purge mongodb-org*
```
after installation:
```bash
sudo systemctl enable mongod
sudo systemctl restart mongod
```

### Enable Replica Set

```bash
sudo systemctl stop bitplay2
sudo systemctl stop mongod
# Manually start server in replica mod to check it:
# mongod --port 26459 --dbpath /var/lib/mongodb --replSet rs0 --bind_ip localhost
```
Change permissions: 
```bash
# storage.dbPath
# systemLog.path
sudo chown -R mongodb:mongodb /var/lib/mongodb && \
sudo chown -R mongodb:mongodb /var/log/mongodb
```


Change config `sudo vim /etc/mongod.conf` 
```
replication:
  replSetName: "rs0"
```
Fix also processManagement: (with update to version 3.6)
```
processManagement:
  timeZoneInfo: /usr/share/zoneinfo
```
`sudo systemctl start mongod`

Initialize it:
```bash
mongo --port 26459 --eval 'rs.initiate()'
```
##### troubleshooting
Assertion: 28595:13: Permission denied src/mongo/db/storage/wiredtiger/wiredtiger_kv_engine.cpp 267
```bash
# storage.dbPath
sudo chown -R mongodb:mongodb /var/lib/mongodb

# systemLog.path
sudo chown -R mongodb:mongodb /var/log/mongodb
```


### To enable transactions:
Make sure you have version > 4.0 and Replica Set(not standalone)
https://docs.mongodb.com/manual/tutorial/convert-standalone-to-replica-set/
https://www.baeldung.com/spring-data-mongodb-transactions


$ sudo vim /etc/mongod.conf
```
net:
  port: 26459
replication:
  replSetName: "rs0"
```
Starting: `mongo 127.0.0.1:26459 --eval "rs.initiate()"`




# Extra mongo commands:

####Checks that mongobeelock is released:
```bash
dsh -Mac -- "mongo bitplay --port 26459 --eval 'db.mongobeelock.stats()' | grep \\\"size"

# Checks settings bitmexContractTypes from mongodb
dsh -Mac -- "mongo bitplay --port 26459 --eval 'db.settingsCollection.find({}).projection({"bitmexContractTypes": 1}).limit(2)'
```


