package info.bitrich.xchangestream.bitmex.wsjsr356;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bitmex.dto.AuthenticateRequest;
import info.bitrich.xchangestream.bitmex.dto.WebSocketMessage;
import info.bitrich.xchangestream.service.exception.NotAuthorizedException;
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.knowm.xchange.bitmex.service.BitmexDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingServiceBitmex {

    private static final Logger log = LoggerFactory.getLogger(StreamingServiceBitmex.class);

    private String apiUrl;
    private WSClientEndpoint clientEndPoint;
    private WSMessageHandler msgHandler;
    private Disposable pingDisposable;
    private int checkReconnect = 2;
    private final Object pingLock = new Object();
    private volatile int disconnectCount = 0;

    public StreamingServiceBitmex(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Completable connect() {
        return Completable.create(completable -> {
            try {
                //TODO how to utilize WS objects on serveral 'connect' calls?
                log.info("connecting to " + apiUrl);
                clientEndPoint = new WSClientEndpoint(new URI(apiUrl));

                msgHandler = new WSMessageHandler();
                clientEndPoint.setMessageHandler(msgHandler);

                completable.onComplete();

            } catch (Exception e1) {
                completable.onError(e1);
            }
        });
    }

    public Completable unsubscribeChannel(String channel, String subscriptionSubject) {
        return Completable.create(emitter -> {
            try {
                if (msgHandler.getChannels().containsKey(channel)) {
                    if (clientEndPoint.isOpen()) {
                        clientEndPoint.sendMessage(getUnsubscribeMessage(subscriptionSubject));
                    }
                    msgHandler.getChannels().remove(channel);
                }
                emitter.onComplete();

            } catch (IOException throwable) {
                emitter.onError(throwable);
            }
        });
    }

    public Observable<JsonNode> subscribeChannel(String channel, String subscriptionSubject) {
        return subscribeChannel(channel, Collections.singletonList(subscriptionSubject));
    }

    public Observable<JsonNode> subscribeChannel(String channel, List<String> subscriptionSubject) {
        log.info("Subscribing to channel {} {}", channel, Arrays.toString(subscriptionSubject.toArray()));

        return Observable.<JsonNode>create(e -> {
            if (clientEndPoint == null || !clientEndPoint.isOpen()) {
                e.onError(new NotConnectedException());
                e.onComplete();
                return;
            }

            if (!msgHandler.isAuthenticated()) {
                throw new NotAuthorizedException();
            }

            msgHandler.getChannels().put(channel, e);
            try {
                for (String s : subscriptionSubject) {
                    clientEndPoint.sendMessage(getSubscribeMessage(s));
                }
            } catch (IOException throwable) {
                e.onError(throwable);
            }
        }).doOnDispose(() -> {
            for (String s : subscriptionSubject) {
                if (clientEndPoint.isOpen()) {
                    clientEndPoint.sendMessage(getUnsubscribeMessage(s));
                }
            }
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
        int currentIter = disconnectCount;
        return Completable.create(onDisconnectEmitter -> {

            msgHandler.setOnDisconnectEmitter(onDisconnectEmitter);

            // Sending 'ping' just to keep the connection alive.
            pingDisposable = Observable.interval(20, 60, TimeUnit.SECONDS)
                    .takeWhile(observer -> currentIter == disconnectCount)
                    .subscribe(aLong -> {

                        synchronized (pingLock) {
                            log.info(currentIter + " ping-pong start");
                            msgHandler.setWaitingForPong();
                            Instant start = Instant.now();

                            for (int i = 0; i < 10; i++) { // 2*10 = 20sec
                                // Sending 'ping'
                                if (!clientEndPoint.isOpen()) {
                                    log.error(currentIter + " Ping failed: clientEndPoint is not open");
                                    onDisconnectEmitter.onComplete();
                                    break;
                                }

                                if (i > 2) {
                                    log.info(currentIter + " Send: ping " + i);
                                }
                                clientEndPoint.sendMessage("ping");

                                boolean success = msgHandler.completablePong().blockingAwait(2, TimeUnit.SECONDS);

                                if (!msgHandler.isWaitingForPong()) {
                                    break;
                                }
                            }

                            Instant end = Instant.now();
                            log.info(currentIter + " ping-pong(sec): " + Duration.between(start, end).getSeconds());

                            if (msgHandler.isWaitingForPong()) {
                                log.error(currentIter + " Ping failed. Timeout on waiting 'pong'.");
                                onDisconnectEmitter.onComplete();
                            }

//                            if (checkReconnect % 2 == 0) {
//                                log.error(currentIter + " CHECK RECONNECT ACTION: DO CLOSE");
//                                clientEndPoint.doClose();
//                            }
//                            checkReconnect++;

                        }
                    }, throwable -> {
                        log.error(currentIter + " Ping failed exception", throwable);
                        onDisconnectEmitter.onComplete();
                    }, () -> log.info(currentIter + " pingDisposable onComplete()"));
        });
    }

    public Completable disconnect() {
        return Completable.create((completableEmitter) -> {
            try {
                log.info(disconnectCount + " disconnect");
                disconnectCount++;
                pingDisposable.dispose();
                clientEndPoint.doClose();
                msgHandler.getChannels().clear();

                completableEmitter.onComplete();

            } catch (Exception e) {
                completableEmitter.onError(e);
            }
        });
    }


    public Completable authenticate(String apiKey, String secretKey, Long nonce) {
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
