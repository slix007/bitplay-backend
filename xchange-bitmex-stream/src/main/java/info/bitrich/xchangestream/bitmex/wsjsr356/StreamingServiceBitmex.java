package info.bitrich.xchangestream.bitmex.wsjsr356;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.bitrich.xchangestream.bitmex.dto.AuthenticateRequest;
import info.bitrich.xchangestream.bitmex.dto.WebSocketMessage;
import info.bitrich.xchangestream.service.exception.NotAuthorizedException;
import info.bitrich.xchangestream.service.exception.NotConnectedException;

import org.knowm.xchange.bitmex.service.BitmexDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import io.reactivex.Completable;
import io.reactivex.Observable;

public class StreamingServiceBitmex {

    private static final Logger log = LoggerFactory.getLogger(StreamingServiceBitmex.class);

    private String apiUrl;
    WSClientEndpoint clientEndPoint;
    WSMessageHandler msgHandler;

    public StreamingServiceBitmex(String apiUrl) throws URISyntaxException {
        this.apiUrl = apiUrl;
    }

    public Completable connect() {
        return Completable.create(completable -> {
            try {
                //TODO how to utilize WS objects on serveral 'connect' calls?
                clientEndPoint = new WSClientEndpoint(new URI(apiUrl));

                msgHandler = new WSMessageHandler();
                clientEndPoint.addMessageHandler(msgHandler);

            } catch (URISyntaxException e1) {
                completable.onError(e1);
            }

            completable.onComplete();
        });
    }

    public Observable<JsonNode> subscribeChannel(String channel, String subscriptionSubject) {
        log.info("Subscribing to channel {}", channel);

        return Observable.<JsonNode>create(e -> {
            if (clientEndPoint == null || !clientEndPoint.isOpen()) {
                e.onError(new NotConnectedException());
            }

            if (!msgHandler.isAuthenticated()) {
                throw new NotAuthorizedException();
            }

            msgHandler.getChannels().put(channel, e);
            try {
                clientEndPoint.sendMessage(getSubscribeMessage(subscriptionSubject));
            } catch (IOException throwable) {
                e.onError(throwable);
            }
        }).doOnDispose(() -> {
            clientEndPoint.sendMessage(getUnsubscribeMessage(subscriptionSubject));
            msgHandler.getChannels().remove(channel);
        });
    }

    private String getSubscribeMessage(String subscriptionSubject) throws IOException {
        WebSocketMessage webSocketMessage = new WebSocketMessage("subscribe", Collections.singletonList(subscriptionSubject));

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(webSocketMessage);
    }

    private String getUnsubscribeMessage(String subscriptionSubject) throws IOException {
        WebSocketMessage webSocketMessage = new WebSocketMessage("unsubscribe", Collections.singletonList(subscriptionSubject));

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(webSocketMessage);
    }

    public Completable onDisconnect() {
        return Completable.never(); //TODO
    }

    public Completable disconnect() {
        return Completable.create((completableEmitter) -> {
            try {
                clientEndPoint.doClose();
                completableEmitter.onComplete();
            } catch (Exception e) {
                completableEmitter.onError(e);
            }
        });
    }


    public Completable sendAuthenticateMessage(String apiKey, String secretKey, Long nonce) {
        return Completable.create(completableEmitter -> {
            msgHandler.setAuthCompleteEmitter(completableEmitter);

            final String authenticateRequest = createAuthenticateRequest(apiKey, secretKey, String.valueOf(nonce));
            clientEndPoint.sendMessage(authenticateRequest);
        });
    }

    private String createAuthenticateRequest(String apiKey, String secretKey, String nonce) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String signatureSource = "GET/realtime" + nonce;
        String signature;
        signature = BitmexDigest.generateBitmexSignature(secretKey, signatureSource);

        final AuthenticateRequest authenticateRequest = new AuthenticateRequest(apiKey, nonce, signature);
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(authenticateRequest);
    }

}
