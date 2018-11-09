package com.bitplay.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setNameFormat("slack-notifications-%d").build());

    private String SLACK_URL = "https://hooks.slack.com/services/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";

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
            String theChannel = "app-messages";
            String localHostName = InetAddress.getLocalHost().getHostName();
            if (localHostName.equals("sergei-XPS-15-9560")) { // local development workaround
                theChannel = "localchannel";
                sendSync("localhost", message, theChannel);
            } else {
                String hostLabel = getHostLabel();
                if (hostLabel == null) { // don't send from unknown hosts
                    return;
                }
                sendSync(hostLabel, message, theChannel);
            }
        } catch (Exception e) {
            log.error("Can not send slack notification", e);
        }
    }

    void sendSync(String hostLabel, String message, String theChannel) {

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

    private String getHostLabel() throws UnknownHostException {
        String hostName = InetAddress.getLocalHost().getHostName();
        if (hostName.startsWith("6") && hostName.length() >= 3) {
            return hostName.substring(0, 3);
        }
        return null; // don't send from 'not servers'.
    }
}
