package info.bitrich.xchangestream.bitmex.wsjsr356;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import info.bitrich.xchangestream.service.exception.NotAuthorizedException;
import io.reactivex.CompletableEmitter;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
public class WSMessageHandler implements WSClientEndpoint.MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(WSClientEndpoint.class);

    private Map<String, ObservableEmitter<JsonNode>> channels = new ConcurrentHashMap<>();
    private boolean isAuthenticated = false;
    private CompletableEmitter authCompleteEmitter;
    private Observable<String> pongObservable;
    private volatile ObservableEmitter pongEmitter;
    private CompletableEmitter onDisconnectEmitter;

    public WSMessageHandler() {
        pongObservable = Observable.<String>create(emitter -> this.pongEmitter = emitter)
                .share();
    }

    @Override
    public void handleMessage(String message) {

        parseAndProcessJsonMessage(message);

    }

    @Override
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
            pongEmitter.onNext("pong");
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

    private void handleJsonMessage(JsonNode jsonMessage) {
        if (!isAuthenticated && authCompleteEmitter != null && !authCompleteEmitter.isDisposed()) {
            checkIfAuthenticationResponse(jsonMessage);
        }

        String channel = parseChannelNameFromMessage(jsonMessage);

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
            if (channelName.equals("orderBookL2")) { //orderBookL2:ETHUSD
                String symbol = message.get("data").get(0).get("symbol").asText();
                channelName = channelName + ":" + symbol;
            }
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

    public Observable<String> getPongObservable() {
        return pongObservable;
    }

}
