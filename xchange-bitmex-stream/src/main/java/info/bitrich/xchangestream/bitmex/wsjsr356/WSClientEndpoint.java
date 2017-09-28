package info.bitrich.xchangestream.bitmex.wsjsr356;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.PathParam;

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
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(final Session userSession) {
        this.userSession = userSession;
        this.open = true;
        log.info("onOpen:" + userSession);
    }

    @OnClose
    public void onClose(final Session userSession, final CloseReason reason) {
        this.userSession = null;
        this.open = false;
        log.info("onClose {} : {}", userSession, reason);
    }
    public void doClose() throws IOException {
        log.info("doClose:" + userSession);
        this.userSession.close();
        this.userSession = null;
        this.open = false;
    }


    @OnMessage(maxMessageSize = 8192 * 1000)
    public void onMessage(final String message) {
        if (messageHandler != null) {
            messageHandler.handleMessage(message);
        }
        log.debug("onMessage:" + userSession + ":" + message);
    }

    public void addMessageHandler(final MessageHandler msgHandler) {
        messageHandler = msgHandler;
    }

    public void sendMessage(final String message) {
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
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }
}