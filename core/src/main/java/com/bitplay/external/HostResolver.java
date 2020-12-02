package com.bitplay.external;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HostResolver {

    public static final String LOCALHOST = "localhost";
    public static final String UNKNOWN = "unknown";

    private String hostname;
    private String hostnameForMetrics;

    public String getHostname() {
        if (hostname == null) {
            resolveHostname();
        }
        return hostname;
    }

    public String getHostnameForMetrics() {
        if (hostnameForMetrics == null) {
            resolveHostname();
        }
        return hostnameForMetrics;
    }

    private void resolveHostname() {
        final String localHostName;
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
            hostname = localHostName.split("-")[0];
            hostnameForMetrics = localHostName.split("\\.")[0];
            if (localHostName.equals("sergei-XPS-15-9560")) {
                hostname = LOCALHOST;
            }
        } catch (UnknownHostException e) {
            log.error("can not resolveHostname", e);
            hostname = UNKNOWN;
            hostnameForMetrics = UNKNOWN;
        }
    }

}
