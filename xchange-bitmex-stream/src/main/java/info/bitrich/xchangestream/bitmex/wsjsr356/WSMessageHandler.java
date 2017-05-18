package info.bitrich.xchangestream.bitmex.wsjsr356;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import info.bitrich.xchangestream.service.exception.NotAuthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.CompletableEmitter;
import io.reactivex.ObservableEmitter;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
public class WSMessageHandler implements WSClientEndpoint.MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(WSClientEndpoint.class);

    private Map<String, ObservableEmitter<JsonNode>> channels = new ConcurrentHashMap<>();
    private boolean isAuthenticated = false;
    private CompletableEmitter authCompleteEmitter;

    @Override
    public void handleMessage(String message) {

        parseAndProcessJsonMessage(message);

    }

    public synchronized Map<String, ObservableEmitter<JsonNode>> getChannels() {
        return channels;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    private void parseAndProcessJsonMessage(String message) {
//        log.info("Received message: {}", message);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode;

        // Parse incoming message to JSON
        try {
            jsonNode = objectMapper.readTree(message);
        } catch (IOException e) {
            if (!message.equals("pong")) {
                log.error("Error parsing incoming message to JSON: {}", message);
            }
            return;
        }

        // In case of array - handle every message separately.
        if (jsonNode.getNodeType().equals(JsonNodeType.ARRAY)) {
            for (JsonNode node : jsonNode) {
                handleJsonMessage(node);
            }
        } else {
            handleJsonMessage(jsonNode);
        }
    }

    private void handleJsonMessage(JsonNode jsonMessage) {
        String channel = parseChannelNameFromMessage(jsonMessage);
        if (channel != null) {
            handleChannelMessage(channel, jsonMessage);
        }
    }

    private String parseChannelNameFromMessage(JsonNode message) {
        String channelName = null;
        if (message.get("success") != null) {
            log.debug("Success response: " + message.toString());
            checkIfAuthenticationResponse(message);
        } else if (message.get("info") != null) {
            log.debug("Connect response: " + message.toString());
        } else if (message.get("error") != null) {
            log.error("Error response: " + message.toString());
            if (message.get("status") != null) {
                final int status = message.get("status").asInt();
                if (status == 401) {
                    throw new NotAuthorizedException();//TODO send to 'errorChannel' (have to create it)
                }
            }

        } else if (message.get("table") != null) {
            channelName = message.get("table").asText();
        }
        return channelName;
    }

    private void checkIfAuthenticationResponse(JsonNode message) {
        //"request":{"op":"authKey"
        try {
            final String op = message.get("request").get("op").asText();
            if (op.equals("authKey")) {
                isAuthenticated = true;
                authCompleteEmitter.onComplete();
            }
        } catch (Exception e) {
            //is not authentication request
        }
    }

    private void handleChannelMessage(String channel, JsonNode message) {
        ObservableEmitter<JsonNode> emitter = channels.get(channel);
        if (emitter == null) {
            log.error("No subscriber for channel {}.", channel);
            return;
        }

        emitter.onNext(message);
    }


    public void setAuthCompleteEmitter(CompletableEmitter authCompleteEmitter) {
        this.authCompleteEmitter = authCompleteEmitter;
    }
}
