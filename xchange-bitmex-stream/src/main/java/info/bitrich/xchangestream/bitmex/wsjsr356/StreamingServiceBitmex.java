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
import java.util.Collections;
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
    private boolean checkReconnect = true;

    public StreamingServiceBitmex(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Completable connect() {
        return Completable.create(completable -> {
            try {
                //TODO how to utilize WS objects on serveral 'connect' calls?
                clientEndPoint = new WSClientEndpoint(new URI(apiUrl));

                msgHandler = new WSMessageHandler();
                clientEndPoint.addMessageHandler(msgHandler);

                completable.onComplete();

            } catch (Exception e1) {
                completable.onError(e1);
            }
        });
    }

    public Observable<JsonNode> subscribeChannel(String channel, String subscriptionSubject) {
        log.info("Subscribing to channel {}", channel);

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
                clientEndPoint.sendMessage(getSubscribeMessage(subscriptionSubject));
            } catch (IOException throwable) {
                e.onError(throwable);
            }
        }).doOnDispose(() -> {
            if (clientEndPoint.isOpen()) {
                clientEndPoint.sendMessage(getUnsubscribeMessage(subscriptionSubject));
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
        return Completable.create(completable -> {

            // Sending 'ping' just to keep the connection alive.
            pingDisposable = Observable.interval(1, 1, TimeUnit.MINUTES)
                    .subscribe(aLong -> {

                        int attempt = 0;
                        boolean sendPingSuccessfully = false;
                        while (attempt < 5 && !sendPingSuccessfully) { // 5*2sec=10sec < 1 min(repeat interval)
                            attempt++;

                            sendPingSuccessfully = Completable.create(e -> {
//                                if (checkReconnect) {
//                                    log.error("CHECK RECONNECT ACTION: DO CLOSE");
//                                    checkReconnect = false;
//                                    clientEndPoint.doClose();
//                                }
                                msgHandler.setPingCompleteEmitter(e);

                                if (!clientEndPoint.isOpen()) {
                                    log.error("Ping failed: clientEndPoint is not open");
                                    completable.onComplete();
                                } else {
                                    log.debug("Send: ping");
                                    clientEndPoint.sendMessage("ping");
                                }

                            }).blockingAwait(2, TimeUnit.SECONDS);

                        }
//                        if (checkReconnect) {
//                            log.error("CHECK RECONNECT ACTION: DO CLOSE");
//                            checkReconnect = false;
////                            clientEndPoint.doClose();
//                            sendPingSuccessfully = false;
//                        }

                        if (!sendPingSuccessfully) {
                            completable.onError(new Exception("Ping failed. Timeout on waiting 'pong'."));
                            log.error("Ping failed");
                        }

                    }, throwable -> {
                        log.error("Ping failed exception", throwable);
                        completable.onError(new Exception("Ping failed exception", throwable));
                        completable.onComplete();
                    });
        });
    }

    public Completable disconnect() {
        return Completable.create((completableEmitter) -> {
            try {
                log.info("disconnect");
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
