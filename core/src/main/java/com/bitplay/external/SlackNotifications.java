package com.bitplay.external;

import com.bitplay.external.DestinationResolver.ToObj;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.log4j.Log4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j
@Service
public class SlackNotifications {

    @Autowired
    private DestinationResolver destinationResolver;

    private final static String SLACK_URL = "https://hooks.slack.com/services/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";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setNameFormat("slack-notifications-%d").build());


    public void sendNotify(NotifyType notifyType, String message) {
        CompletableFuture.runAsync(() -> sendSync(notifyType, message), executor);
    }


    private void sendSync(NotifyType notifyType, String message) {
        try {
            final ToObj toObj = destinationResolver.defineWhereToSend(notifyType);

            if (toObj.getChannel() != null) {
                sendSync(toObj.getChannel(), toObj.getHostLabel(), message);
                if (toObj.getNightChannel() != null) {
                    sendSync(toObj.getNightChannel(), toObj.getHostLabel(), message);
                }
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

}
