package com.bitplay.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Service;

@Log4j
@Service
public class SlackNotifications {

    private final static List<String> testServers = Arrays.asList(
            "658",
            "660");
    private final static List<String> prodServers = Arrays.asList(
            "659",
            "662",
            "667",
            "668",
            "669");

    private final static String SLACK_URL = "https://hooks.slack.com/services/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";
    private final static String LOCAL_CHANNEL = "app-local";
    private final static String TEST_CHANNEL = "app-test";
    private final static String PROD_CHANNEL = "app-prod";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setNameFormat("slack-notifications-%d").build());

    private Map<String, Instant> toThrottle = new HashMap<>();

    public void sendNotifyThrottled(String objectToThrottle, String message) {
        CompletableFuture.runAsync(() -> sendSyncThrottled(objectToThrottle, message), executor);
    }

    public void sendNotify(String message) {
        CompletableFuture.runAsync(() -> sendSync(message), executor);
    }

    private void sendSyncThrottled(String objectToThrottle, String message) {
        try {
            Instant lastRun = toThrottle.get(objectToThrottle);
            if (lastRun == null || Duration.between(lastRun, Instant.now()).getSeconds() > 30) {
                sendSync(message);
                toThrottle.put(objectToThrottle, Instant.now());
            }
        } catch (Exception e) {
            log.error("Can not send slack notification", e);
        }
    }

    private void sendSync(String message) {
        try {
            final ToObj toObj = defineWhereToSend();

            if (toObj.getChannel() != null) {
                sendSync(toObj.getChannel(), toObj.getHostLabel(), message);
            }

        } catch (Exception e) {
            log.error("Can not send slack notification", e);
        }
    }

    void sendSync(String theChannel, String hostLabel, String message) {

        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(SLACK_URL);
            String theText = hostLabel + ": " + message;

            NotifyObject notifyObject = new NotifyObject(theText, theChannel);
            ObjectMapper objMapper = new ObjectMapper();
            String json = objMapper.writeValueAsString(notifyObject);

            log.debug(json);
            StringEntity entity = new StringEntity(json);
            entity.setContentType("application/json");
            httpPost.setEntity(entity);

            //noinspection unused
            CloseableHttpResponse response = client.execute(httpPost);
//            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
            client.close();

        } catch (IOException e) {
            log.error("Can not send slack notification", e);
        }
    }

    private ToObj defineWhereToSend() throws UnknownHostException {
        final String localHostName = InetAddress.getLocalHost().getHostName();
        String theChannel = null;
        String hostLabel = localHostName.substring(0, 3);
        if (localHostName.equals("sergei-XPS-15-9560")) { // local development workaround
            hostLabel = "localhost";
            theChannel = LOCAL_CHANNEL;
        } else if (testServers.contains(hostLabel)) {
            theChannel = TEST_CHANNEL;
        } else if (prodServers.contains(hostLabel)) {
            theChannel = PROD_CHANNEL;
        }
        return new ToObj(theChannel, hostLabel);
    }

    @AllArgsConstructor
    @Getter
    private static class ToObj {

        private String channel;
        private String hostLabel;
    }
}
