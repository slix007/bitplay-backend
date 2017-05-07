package info.bitrich.xchangestream.bitmex.netty;

import info.bitrich.xchangestream.service.exception.NotConnectedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;

import static io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE;

public abstract class BitmexNettyStreamingService<T> {
    private static final Logger LOG = LoggerFactory.getLogger(BitmexNettyStreamingService.class);

    private final URI uri;
    private Channel webSocketChannel;
    private Map<String, ObservableEmitter<T>> channels = new ConcurrentHashMap<>();

    public BitmexNettyStreamingService(String apiUrl) {
        try {
            uri = new URI(apiUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Error parsing URI " + apiUrl, e);
        }
    }

    public Completable connect(long heartbeatInterval, TimeUnit heartbeatTimeUnit) {
        return Completable.create(completable -> {
            try {
                String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();

                String host = uri.getHost();
                if (host == null) {
                    throw new IllegalArgumentException("Host cannot be null.");
                }

                int port;
                if (uri.getPort() == -1) {
                    if ("ws".equalsIgnoreCase(scheme)) {
                        port = 80;
                    } else if ("wss".equalsIgnoreCase(scheme)) {
                        port = 443;
                    } else {
                        port = -1;
                    }
                } else {
                    port = uri.getPort();
                }

                LOG.info("Connecting to {}://{}:{}{}", scheme, host, port, uri.getPath());

                if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                    throw new IllegalArgumentException("Only WS(S) is supported.");
                }

                final boolean ssl = "wss".equalsIgnoreCase(scheme);
                final SslContext sslCtx;
                if (ssl) {
                    sslCtx = SslContextBuilder.forClient().build();
                } else {
                    sslCtx = null;
                }

                EventLoopGroup group = new NioEventLoopGroup();

                final BitmexWebSocketClientHandler handler =
                        new BitmexWebSocketClientHandler(WebSocketClientHandshakerFactory.newHandshaker(
                                uri,
                                WebSocketVersion.V13,
                                null,
                                true,
                                new DefaultHttpHeaders()),
                                this::massegeHandler);

                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline p = ch.pipeline();
                                if (sslCtx != null) {
                                    p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                                }
                                final WebSocketClientExtensionHandler instance = new WebSocketClientExtensionHandler(
                                        new PerMessageDeflateClientExtensionHandshaker(6, ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(),
                                                MAX_WINDOW_SIZE, true, false),
                                        new DeflateFrameClientExtensionHandshaker(false),
                                        new DeflateFrameClientExtensionHandshaker(true));
                                p.addLast(
                                        new HttpClientCodec(),
                                        new HttpObjectAggregator(8192),
                                        instance,
                                        handler);
                            }
                        });

                b.connect(uri.getHost(), port).addListener((ChannelFuture future) -> {
                    webSocketChannel = future.channel();
                    handler.handshakeFuture().addListener(future1 -> {
                        completable.onComplete();
                        // Schedule Heartbeat
                        webSocketChannel.eventLoop().scheduleAtFixedRate(() -> sendMessage("ping"),
                                heartbeatInterval, heartbeatInterval, heartbeatTimeUnit);

                    });
                });
            } catch (Exception throwable) {
                completable.onError(throwable);
            }
        });
    }

    public Completable onDisconnect() {
        return Completable.create(completable -> {
            LOG.info("register Completable onDisconnect");
            webSocketChannel.closeFuture().addListener(future -> {
                LOG.error("onDisconnect has been invoked == webSocketChannel has been closed.");
                completable.onComplete();
            });
        });
    }

    public Completable disconnect() {
        return Completable.create(completable -> {
            CloseWebSocketFrame closeFrame = new CloseWebSocketFrame();
            webSocketChannel.writeAndFlush(closeFrame).addListener(future -> {
                channels = new ConcurrentHashMap<>();
                completable.onComplete();
            });
        });
    }

    protected abstract String getChannelNameFromMessage(T message) throws IOException;

    public abstract String getSubscribeMessage(String channelName) throws IOException;

    public abstract String getUnsubscribeMessage(String channelName) throws IOException;

    /**
     * Handler that receives incoming messages.
     *
     * @param message Content of the message from the server.
     */
    public abstract void massegeHandler(String message);

    public void sendMessage(String message) {
        LOG.debug("Sending message: {}", message);

        if (webSocketChannel == null || !webSocketChannel.isOpen()) {
            LOG.warn("WebSocket is not open! Call connect first.");
            return;
        }

        if (!webSocketChannel.isWritable()) {
            LOG.warn("Cannot send data to WebSocket as it is not writable.");
            return;
        }

        WebSocketFrame frame = new TextWebSocketFrame(message);
        webSocketChannel.writeAndFlush(frame);
    }

    public Observable<T> subscribeChannel(String tableName, String symbol, String channelName) {
        LOG.info("Subscribing to channel {}", tableName);

        return Observable.<T>create(e -> {
            if (webSocketChannel == null || !webSocketChannel.isOpen()) {
                e.onError(new NotConnectedException());
            }

            channels.put(tableName, e);
            try {
                sendMessage(getSubscribeMessage(channelName));
            } catch (IOException throwable) {
                e.onError(throwable);
            }
        }).doOnDispose(() -> {
            sendMessage(getUnsubscribeMessage(tableName));
            channels.remove(tableName);
        });
    }

    protected String getChannel(T message) {
        String channel;
        try {
            channel = getChannelNameFromMessage(message);
        } catch (IOException e) {
            LOG.error("Cannot parse channel from message: {}", message);
            return "";
        }
        return channel;
    }

    protected void handleMessage(T message) {
        String channel = getChannel(message);
        handleChannelMessage(channel, message);
    }

    protected void handleError(T message, Throwable t) {
        String channel = getChannel(message);
        handleChannelError(channel, t);
    }


    protected void handleChannelMessage(String channel, T message) {
        ObservableEmitter<T> emitter = channels.get(channel);
        if (emitter == null) {
            LOG.debug("No subscriber for channel {}.", channel);
            return;
        }

        emitter.onNext(message);
    }

    protected void handleChannelError(String channel, Throwable t) {
        ObservableEmitter<T> emitter = channels.get(channel);
        if (emitter == null) {
            LOG.debug("No subscriber for channel {}.", channel);
            return;
        }

        emitter.onError(t);
    }
}
