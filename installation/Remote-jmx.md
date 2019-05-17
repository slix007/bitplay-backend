# General instruction

Add the following JVM Properties in "$JAVA_OPTS" (in your application):

```
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=<PORT_NUMBER>
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Djava.rmi.server.hostname=<HOST'S_IP>
```

In the Jconsole/Visual VM use the following to connect:
```
service:jmx:rmi:///jndi/rmi://<HOST'S_IP>:<PORT_NUMBER>/jmxrmi
```

### How to check a server

- check the `java.rmi.server.hostname` and `com.sun.management.jmxremote.port` params using `ps aux | grep java`
- if something wrong change it in the `/etc/systemd/system/bitplay2.service`
- make sure the `-Djava.rmi.server.hostname=<HOST'S_IP>` is correct
- connect via jvisualvm to remote host (jstatd connection) with
`-Djava.rmi.server.hostname=`
and
 `-Dcom.sun.management.jmxremote.port=<PORT_NUMBER>`

### Common ports in project
jmx/rmi 3583
debug port 3783


##### changing settings:
```bash
sudo vim /etc/systemd/system/bitplay2.service
sudo systemctl daemon-reload
sudo systemctl restart bitplay2.service
```
