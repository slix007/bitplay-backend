package com.bitplay.xchangestream.bitmex.wsjsr356;

import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
public class WSClientEndpoint extends Endpoint {
    private static final Logger log = LoggerFactory.getLogger(WSClientEndpoint.class);

    private final AtomicReference<Session> userSession = new AtomicReference<>(null);

    private volatile WSMessageHandler messageHandler;

    public WSClientEndpoint(final URI endpointURI) {
        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            client.setAsyncSendTimeout(15000);
            log.info("DefaultAsyncSendTimeout=" + client.getDefaultAsyncSendTimeout()
                    + ". endpointURI=" + endpointURI
                    + ". class=" + client.getClass()
            );
            final Session session = client.connectToServer(this, cec, endpointURI);
            userSession.set(session);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        this.userSession.set(session);
        log.info("onOpen userSession=" + session.getId());
        /// check onClose
//        Observable.timer(20, TimeUnit.SECONDS).subscribe(aLong -> {
//            userSession.close(new CloseReason(CloseCodes.NO_STATUS_CODE, "TEST close"));
//        });
    }


    @Override
    public void onClose(final Session session, final CloseReason reason) {
        if (session != null) {
            log.info("onClose session={}, openSessions={}, URI={} : {}", session.getId(), session.getOpenSessions(),
                    session.getRequestURI(),
                    reason);
        } else {
            log.info("onClose session=null: {}", reason);
        }
        this.userSession.set(null);
        if (messageHandler != null) {
            messageHandler.onCloseTrigger();
        }
    }

    public void doClose() throws IOException {
        final Session session = userSession.get();
        log.info("doClose userSession=" + (session == null ? "null" : session.getId()));
        if (session != null) {
            session.close(new CloseReason(CloseCodes.NO_STATUS_CODE, "Manual doClose"));
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {

        log.error("onError userSession=" + (session == null ? "null" : session.getId()), thr);

    }

    public void addMessageHandler(final WSMessageHandler msgHandler) {
        userSession.get().addMessageHandler(msgHandler);
        messageHandler = msgHandler;
    }

    public synchronized void sendMessage(final String message) {
        userSession.get().getAsyncRemote().sendText(message);
    }

    public boolean isOpen() {
        final Session session = userSession.get();
        return session != null && session.isOpen();
    }
}