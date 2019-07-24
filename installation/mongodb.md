Current in use version 3.2.X





To use 4.0.10.
Manual download:
https://www.mongodb.com/download-center/community
https://repo.mongodb.org/apt/ubuntu/dists/bionic/mongodb-org/4.0/multiverse/binary-amd64/mongodb-org-server_4.0.10_amd64.deb

Deb package:
https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/


Upgrade instruction https://optimalbi.com/blog/2018/05/16/upgrading-mongodb-3-4-to-3-6-on-ubuntu-16-04-easy-as-microwave-pie/


To enable transactions:
https://docs.mongodb.com/manual/tutorial/convert-standalone-to-replica-set/
https://www.baeldung.com/spring-data-mongodb-transactions


$ sudo vim /etc/mongod.conf
net:
  port: 26459
replication:
  replSetName: "rs0"




# starting
mongo 127.0.0.1:26459 --eval "rs.initiate()"


