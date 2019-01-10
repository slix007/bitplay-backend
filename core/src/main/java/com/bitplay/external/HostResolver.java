package com.bitplay.external;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class HostResolver {

    public static final String LOCALHOST = "localhost";
    public static final String UNKNOWN = "unknown";

    private String hostname;

    public String getHostname() {
        if (hostname == null) {
            resolveHostname();
        }
        return hostname;
    }

    private void resolveHostname() {
        final String localHostName;
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
            hostname = localHostName.substring(0, 3);
            if (localHostName.equals("sergei-XPS-15-9560")) {
                hostname = LOCALHOST;
            }
        } catch (UnknownHostException e) {
            hostname = UNKNOWN;
        }
    }

}
