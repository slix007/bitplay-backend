package com.bitplay.xchangestream.bitmex.wsjsr356;

import com.bitplay.service.exception.NotAuthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.ObservableEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.MessageHandler;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
public class WSMessageHandler implements MessageHandler.Whole<String> {

    private static final Logger log = LoggerFactory.getLogger(WSMessageHandler.class);

    private Map<String, ObservableEmitter<JsonNode>> channels = new ConcurrentHashMap<>();
    private boolean isAuthenticated = false;
    private CompletableEmitter authCompleteEmitter;
    private final AtomicReference<CompletableEmitter> pongEmitter = new AtomicReference<>();
    private AtomicReference<Boolean> waitingForPong = new AtomicReference<>(false);
    private CompletableEmitter onDisconnectEmitter;

    @Override
    public void onMessage(String message) {
        parseAndProcessJsonMessage(message);
    }

    public void onCloseTrigger() {
        onDisconnectEmitter.onComplete();
    }

    public synchronized Map<String, ObservableEmitter<JsonNode>> getChannels() {
        return channels;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    private void parseAndProcessJsonMessage(String message) {
        if (message.equals("pong")) {
            log.debug("Received message: " + message);
            waitingForPong.set(false);
            CompletableEmitter completableEmitter = pongEmitter.get();
            if (completableEmitter != null && !completableEmitter.isDisposed()) {
                completableEmitter.onComplete();
            }
        } else {
            log.debug("Received message: " + message);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode;

            // Parse incoming message to JSON
            try {
                jsonNode = objectMapper.readTree(message);
            } catch (IOException e) {
                log.error("Error parsing incoming message to JSON: {}", message);
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
    }

    private boolean startFlag = false;

    private void handleJsonMessage(JsonNode jsonMessage) {
        if (!isAuthenticated && authCompleteEmitter != null && !authCompleteEmitter.isDisposed()) {
            checkIfAuthenticationResponse(jsonMessage);
        }

        String channel = parseChannelNameFromMessage(jsonMessage);

        if (!startFlag && channel != null && channel.equals("orderBookL2_25")) {
            startFlag = true;
            log.info("OBThreadSubscriber: " + Thread.currentThread().getName() + ". " + jsonMessage);
        }

        if (channel != null) {
            handleChannelMessage(channel, jsonMessage);
        }
    }

    private String parseChannelNameFromMessage(JsonNode message) {
        String channelName = null;
        if (message.get("success") != null) {
            log.debug("Success response: " + message.toString());
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
//            if (channelName.equals("orderBookL2")) { //orderBookL2:ETHUSD
//                String symbol = message.get("data").get(0).get("symbol").asText();
//                channelName = channelName + ":" + symbol;
//            }
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

    public void setOnDisconnectEmitter(CompletableEmitter onDisconnectEmitter) {
        this.onDisconnectEmitter = onDisconnectEmitter;
    }

    public void setWaitingForPong() {
        this.waitingForPong.set(true);
    }

    public Boolean isWaitingForPong() {
        return waitingForPong.get();
    }

    public Completable completablePong() {
        return Completable.create(this.pongEmitter::set);
    }
}
