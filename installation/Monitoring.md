## Monitoring tools installation

See installation instruction at https://portal.influxdata.com/downloads/ 
https://portal.influxdata.com/downloads/

#### influxdb

See https://portal.influxdata.com/downloads
https://habr.com/post/331016/
```bash
wget https://dl.influxdata.com/influxdb/releases/influxdb_1.6.3_amd64.deb
sudo dpkg -i influxdb_1.6.3_amd64.deb

sudo systemctl start influxdb.service
sudo systemctl enable influxdb.service
```

#### chronograph
...

#### telegraf
```bash
wget https://dl.influxdata.com/telegraf/releases/telegraf_1.8.1-1_amd64.deb
sudo dpkg -i telegraf_1.8.1-1_amd64.deb

sudo systemctl start telegraf.service
sudo systemctl enable telegraf.service
```

#### graphana
http://docs.grafana.org/installation/debian/

```bash
wget https://s3-us-west-2.amazonaws.com/grafana-releases/release/grafana_5.3.0_amd64.deb 
sudo dpkg -i grafana_5.3.0_amd64.deb 

sudo /bin/systemctl daemon-reload
sudo /bin/systemctl start grafana-server
sudo /bin/systemctl enable grafana-server
```

#### Secure databases 
Set passwords
```bash

#Создаём базу и пользователей:
influx -execute 'CREATE DATABASE telegraf'
influx -execute 'CREATE USER admin WITH PASSWORD "password_for_admin" WITH ALL PRIVILEGES'
influx -execute 'CREATE USER telegraf WITH PASSWORD "password_for_telegraf"'
influx -execute 'CREATE USER grafana WITH PASSWORD "password_for_grafana"'
influx -execute 'GRANT WRITE ON "telegraf" TO "telegraf"' #чтобы telegraf мог писать метрики в бд
influx -execute 'GRANT READ ON "telegraf" TO "grafana"' #чтобы grafana могла читать метрики из бд
#в конфиге /etc/influxdb/influxdb.conf в секции [http] меняем параметр auth-enabled для включения авторизации:
sed -i 's|  # auth-enabled = false|  auth-enabled = true|g' /etc/influxdb/influxdb.conf
systemctl restart influxdb
```
The same for the `springboot` DB.