package info.bitrich.xchangestream.bitmex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.bitrich.xchangestream.bitmex.dto.WebSocketMessage;
import info.bitrich.xchangestream.bitmex.netty.BitmexJsonNettyStreamingService;

import org.knowm.xchange.exceptions.ExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

public class BitmexStreamingServiceBitmex extends BitmexJsonNettyStreamingService {

    private static final Logger LOG = LoggerFactory.getLogger(BitmexJsonNettyStreamingService.class);

    public BitmexStreamingServiceBitmex(String apiUrl) {
        super(apiUrl);
    }

    @Override
    protected String getChannelNameFromMessage(JsonNode message) throws IOException {
//        {"info":"Welcome to the BitMEX Realtime API.","version":"1.2.0","timestamp":"2017-05-06T18:46:38.205Z","docs":"https://www.bitmex.com/app/wsAPI","heartbeatEnabled":false}
        final String channelName = (message != null && message.get("table") != null)
                ? message.get("table").asText()
                : "null";
        return channelName;
    }

    @Override
    public String getSubscribeMessage(String channelName) throws IOException {
        WebSocketMessage webSocketMessage = new WebSocketMessage("subscribe", Collections.singletonList(channelName));

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(webSocketMessage);
    }

    @Override
    public String getUnsubscribeMessage(String channelName) throws IOException {
        WebSocketMessage webSocketMessage = new WebSocketMessage("unsubscribe", Collections.singletonList(channelName));

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(webSocketMessage);
    }

    @Override
    protected void handleMessage(JsonNode message) {
        if (message.get("success") != null) {
            LOG.debug("Success response: " + message.toString());
            boolean success = message.get("success").asBoolean();
            if (!success) {
                super.handleError(message, new ExchangeException("Error code: " + message.get("errorcode").asText()));
            }
        } else if (message.get("info") != null) {
            LOG.debug("Connect response: " + message.toString());
        } else {
            super.handleMessage(message);
        }
    }
}
