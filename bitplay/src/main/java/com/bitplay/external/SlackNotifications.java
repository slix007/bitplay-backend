package com.bitplay.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SlackNotifications {

    @Autowired
    private DestinationResolverByFile destinationResolver;

    @Value("${slack.url}")
    private String slackUrl;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setNameFormat("slack-notifications-%d").build());


    public void sendNotify(NotifyType notifyType, String message) {
        CompletableFuture.runAsync(() -> sendSync(notifyType, message), executor);
    }


    private void sendSync(NotifyType notifyType, String message) {
        try {
            if (shouldSkip(notifyType)) {
                return;
            }

            final Destination toObj = destinationResolver.defineWhereToSend(notifyType);

            for (String channel : toObj.getChannels()) {
                sendSync(channel, toObj.getHostLabel(), message);
            }

        } catch (Exception e) {
            log.error("Can not send slack notification", e);
        }
    }

    void sendSync(String theChannel, String hostLabel, String message) {

        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(slackUrl);
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


    private Map<String, Instant> toThrottle = new ConcurrentHashMap<>();

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

    public void resetThrottled(NotifyType notifyType) {
        try {
            String objectToThrottle = notifyType.name();
            toThrottle.remove(objectToThrottle);
        } catch (Exception e) {
            log.error("Can not reset the throttled slack notification " + notifyType, e);
        }

    }

}
