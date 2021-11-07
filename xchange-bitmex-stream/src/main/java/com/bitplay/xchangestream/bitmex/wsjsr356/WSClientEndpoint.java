package com.bitplay.xchangestream.bitmex.wsjsr356;

import java.io.IOException;
import java.net.URI;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 5/16/17.
 */
@ClientEndpoint
public class WSClientEndpoint {
    private static final Logger log = LoggerFactory.getLogger(WSClientEndpoint.class);

    private Session userSession = null;
    private MessageHandler messageHandler;
    private boolean open;

    public WSClientEndpoint(final URI endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setAsyncSendTimeout(15000);
            log.info("DefaultAsyncSendTimeout=" + container.getDefaultAsyncSendTimeout()
                    + ". endpointURI=" + endpointURI
                    + ". class=" + container.getClass()
            );

            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(final Session userSession) {
        this.userSession = userSession;
        this.open = true;
        log.info("onOpen userSession=" + userSession.getId());
        /// check onClose
//        Observable.timer(20, TimeUnit.SECONDS).subscribe(aLong -> {
//            userSession.close(new CloseReason(CloseCodes.NO_STATUS_CODE, "TEST close"));
//        });
    }

    @OnClose
    public void onClose(final Session userSession, final CloseReason reason) {
//        this.userSession = null;
        this.open = false;
        if (userSession != null) {
            log.info("onClose userSession={}, openSessions={}, URI={} : {}", userSession.getId(), userSession.getOpenSessions(),
                    userSession.getRequestURI(),
                    reason);
        } else {
            log.info("onClose userSession=null: {}", reason);
        }
        messageHandler.onCloseTrigger();
    }
    public void doClose() throws IOException {
        log.info("doClose userSession=" + (userSession == null ? "null" : userSession.getId()));
        if (this.open && userSession != null) {
            this.userSession.close(new CloseReason(CloseCodes.NO_STATUS_CODE, "Manual doClose"));
//            this.userSession = null;
        }
        if (this.open) {
            this.open = false;
        }
    }

    @OnMessage(maxMessageSize = 8192 * 1000)
    public void onMessage(final String message) {
        if (userSession == null || !userSession.isOpen()) {
            return;
        }

        if (messageHandler != null) {
            messageHandler.handleMessage(message);
        }
    }

    public void setMessageHandler(final MessageHandler msgHandler) {
        messageHandler = msgHandler;
    }

    public synchronized void sendMessage(final String message) {
        userSession.getAsyncRemote().sendText(message);
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public interface MessageHandler {
        void handleMessage(String message);

        void onCloseTrigger();
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }
}