package com.bitplay.external;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DestinationResolver {

    private final static List<String> testServers = Arrays.asList(
            "658",
            "660");
    private final static List<String> prodServers = Arrays.asList(
            "659",
            "662",
            "667",
            "668",
            "669");

    private final static String LOCAL_CHANNEL = "app-local";
    private final static String TEST_CHANNEL = "app-test";
    private final static String TEST_CHANNEL_NIGHT = "app-test-night";
    private final static String PROD_CHANNEL = "app-prod";
    private final static String PROD_CHANNEL_NIGHT = "app-prod-night";

    ToObj defineWhereToSend(NotifyType notifyType) throws UnknownHostException {
        if (shouldSkip(notifyType)) {
            return new ToObj(null, null, null);
        }

        final String localHostName = InetAddress.getLocalHost().getHostName();
        String theChannel = null;
        String nightChannel = null;
        String hostLabel = localHostName.substring(0, 3);
        if (localHostName.equals("sergei-XPS-15-9560")) { // local development workaround
            hostLabel = "localhost";
            theChannel = LOCAL_CHANNEL;
        } else if (testServers.contains(hostLabel)) {
            theChannel = TEST_CHANNEL;
            nightChannel = notifyType.isNight() ? TEST_CHANNEL_NIGHT : null;
        } else if (prodServers.contains(hostLabel)) {
            theChannel = PROD_CHANNEL;
            nightChannel = notifyType.isNight() ? PROD_CHANNEL_NIGHT : null;
        }
        return new ToObj(theChannel, nightChannel, hostLabel);
    }

    private Map<String, Instant> toThrottle = new HashMap<>();

    private boolean shouldSkip(NotifyType notifyType) {
        if (!notifyType.isThrottled()) {
            return false;
        }

        boolean skipThisOne = true;
        String objectToThrottle = notifyType.name();

        try {
            Instant lastRun = toThrottle.get(objectToThrottle);
            int waitingSec = notifyType.getThrottleSec();
            if (lastRun == null || Duration.between(lastRun, Instant.now()).getSeconds() > waitingSec) {
                skipThisOne = false;
                toThrottle.put(objectToThrottle, Instant.now());
            }
        } catch (Exception e) {
            log.error("Can not throttle slack notification", e);
        }
        return skipThisOne;
    }


    @AllArgsConstructor
    @Getter
    static class ToObj {

        private String channel;
        private String nightChannel;
        private String hostLabel;
    }

}
