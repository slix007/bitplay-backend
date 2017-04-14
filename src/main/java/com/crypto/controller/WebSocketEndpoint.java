package com.crypto.controller;

import com.crypto.service.BitplayUIServicePoloniex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

/**
 * Created by Sergey Shurmin on 4/13/17.
 */
//@Component
@ServerEndpoint("/market/socket")
public class WebSocketEndpoint {

    private final Logger logger = LoggerFactory.getLogger(WebSocketEndpoint.class);

    @Autowired
    private BitplayUIServicePoloniex poloniex;

    private static Set<Session> peers = Collections.synchronizedSet(new HashSet<>());

    @OnMessage
    public String onMessage(String message, Session session) {
        logger.debug("received message from client " + session.getId());
            /*for (Session s : peers) {
                try {
                    s.getBasicRemote().sendText(message);
                    logger.debug("send message to peer ");
                } catch (IOException e) {
                    logger.error("Exception onMessage ", e);
                }
            }*/

        return "message was received by socket mediator and processed: " + message;
    }

    public void sendLogMessage(String message) {
        for (Session s : peers) {
            try {
                s.getBasicRemote().sendText(message);
                logger.debug("send message to peer ");
            } catch (IOException e) {
                logger.error("Exception onMessage ", e);
            }

        }

    }

    @OnOpen
    public void onOpen(Session session, @PathParam("client-id") String clientId) {
        logger.debug("mediator: opened websocket channel for client " + clientId);

        peers.add(session);

        try {
            session.getBasicRemote().sendText("good to be in touch");
        } catch (IOException e) {
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("client-id") String clientId) {
        logger.debug("mediator: closed websocket channel for client " + clientId);
        peers.remove(session);
    }


}
