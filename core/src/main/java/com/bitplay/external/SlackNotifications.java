package com.bitplay.external;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
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

    private String SLACK_URL = "https://hooks.slack.com/services/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";

    public void sendNotify(String message) {
        CompletableFuture.runAsync(() -> sendNotifySync(message));
    }

    private void sendNotifySync(String message) {

        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(SLACK_URL);

            String hostName = getHostName();
            if (hostName == null) {
                return;
            }

            String theText = hostName + ": " + message;

            String json = "{\"text\": \"" + theText + "\"}";
            StringEntity entity = new StringEntity(json);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            //noinspection unused
            CloseableHttpResponse response = client.execute(httpPost);
//            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
            client.close();

        } catch (IOException e) {
            log.error("Can not send slack notification", e);
        }
    }

    private String getHostName() throws UnknownHostException {
        String hostName = InetAddress.getLocalHost().getHostName();
        if (hostName.startsWith("6") && hostName.length() >= 3) {
            return hostName.substring(0, 3);
        }
        return null; // don't send from 'not servers'.
    }
}
