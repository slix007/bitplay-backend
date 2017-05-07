package info.bitrich.xchangestream.bitmex.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class BitmexJsonNettyStreamingService extends BitmexNettyStreamingService<JsonNode> {
    private static final Logger LOG = LoggerFactory.getLogger(BitmexJsonNettyStreamingService.class);

    public BitmexJsonNettyStreamingService(String apiUrl) {
        super(apiUrl);
    }

    @Override
    public void massegeHandler(String message) {
        LOG.debug("Received message: {}", message);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode;

        // Parse incoming message to JSON
        try {
            jsonNode = objectMapper.readTree(message);
        } catch (IOException e) {
            if (!message.equals("pong")) {
                LOG.error("Error parsing incoming message to JSON: {}", message);
            }
            return;
        }

        // In case of array - handle every message separately.
        if (jsonNode.getNodeType().equals(JsonNodeType.ARRAY)) {
            for (JsonNode node : jsonNode) {
                handleMessage(node);
            }
        } else {
            handleMessage(jsonNode);
        }
    }
}
