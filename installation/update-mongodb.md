Current in use version 3.2.22, Standalone.
The recomendation to use Replica Set v4.0

Update 1 server with current instruction without troubles: 15 min.


# Upgrade mongodb 3.2 to 4.0
#### 1) 3.2 -> 3.4

```bash
sudo systemctl stop bitplay2

wget https://repo.mongodb.org/apt/ubuntu/dists/xenial/mongodb-org/3.4/multiverse/binary-amd64/mongodb-org-server_3.4.22_amd64.deb
sudo systemctl stop mongod
sudo dpkg -i mongodb-org-server_3.4.22_amd64.deb
sudo systemctl start mongod

```
Go to mongo console and set new compatibility version
```bash
$ mongo --port 26459
# check 
> db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )
# set new
> db.adminCommand({setFeatureCompatibilityVersion: "3.4"})
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

wget https://repo.mongodb.org/apt/ubuntu/dists/xenial/mongodb-org/3.6/multiverse/binary-amd64/mongodb-org-server_3.6.13_amd64.deb
sudo systemctl stop mongod
sudo dpkg -i mongodb-org-server_3.6.13_amd64.deb
# If it asks to replace the file at /etc/mongod.conf just say no to keep your settings as they are.
sudo systemctl start mongod

```
Go to mongo console and set new compatibility version
```bash
$ mongo --port 26459
# check 
> db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )
# set new
> db.adminCommand({setFeatureCompatibilityVersion: "3.6"})
```


### 3) 3.6 -> 4.0 from apt repository
`sudo systemctl stop mongod`

Check ubuntu version `lsb_release -a`
Use proper repositorie from: https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/
```bash
wget -qO - https://www.mongodb.org/static/pgp/server-4.0.asc | sudo apt-key add -
echo "deb ....."
lsb_release -a
ls /etc/apt/sources.list.d/
sudo rm /etc/apt/sources.list.d/mongodb-org-3.4.list
# use all commands from the instruction above
sudo apt-get update
sudo apt-get install -y mongodb-org
apt search mongo | grep mongo
sudo apt dist-upgrade
``` 
Go to mongo console and set new compatibility version
```bash
$ sudo systemctl start mongod
$ mongo --port 26459
# check 
> db.adminCommand( { getParameter: 1, featureCompatibilityVersion: 1 } )
# set new
> db.adminCommand({setFeatureCompatibilityVersion: "4.0"})
```

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
#mongod --port 26459 --dbpath /var/lib/mongodb --replSet rs0 --bind_ip localhost
```
Change config `sudo vim /etc/mongod.conf` 
```
replication:
  replSetName: "rs0"
```
`sudo systemctl start mongod`

Initialize it:
```bash
$ mongo --port 26459
> rs.initiate()
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



