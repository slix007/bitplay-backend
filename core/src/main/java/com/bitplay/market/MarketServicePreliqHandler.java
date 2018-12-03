package com.bitplay.market;

import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MarketServicePreliqHandler {

    @Autowired
    private BitmexService bitmexService;
    @Autowired
    private OkCoinService okCoinService;
    @Autowired
    private SlackNotifications slackNotifications;

    private final ScheduledExecutorService btmExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("bitmex-preliq-queue-listener-%d").build());
    private final ScheduledExecutorService okExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("okex-preliq-queue-listener-%d").build());

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        btmExecutor.scheduleWithFixedDelay(() -> {
            try {
                queueHandler(bitmexService);
            } catch (Exception e) {
                log.error("Error on checkForDecreasePosition", e);
            }
        }, 30, 1, TimeUnit.SECONDS);
        okExecutor.scheduleWithFixedDelay(() -> {
            try {
                queueHandler(okCoinService);
            } catch (Exception e) {
                log.error("Error on checkForDecreasePosition", e);
            }
        }, 30, 1, TimeUnit.SECONDS);
    }

    private void queueHandler(MarketServicePreliq marketService) throws InterruptedException {
        final BlockingQueue<PlaceOrderArgs> preliqQueue = marketService.getPreliqQueue();
        final PlaceOrderArgs item = preliqQueue.peek(); // start "transaction"
        if (item == null) {
            return;
        }

        Instant preliqQueuedTime = item.getPreliqQueuedTime();

        if (isExpired(preliqQueuedTime)) {
            final String msg = "preliq is expired." + item;
            log.info(msg);
            marketService.getTradeLogger().info(msg);
            preliqQueue.remove(item); // skip old
            return;
        }

        doPreliqOrder(item, marketService);

        marketService.resetPreliqState();

        preliqQueue.remove(item); // end "transaction"
    }

    private boolean isExpired(Instant preliqQueuedTime) {
        return preliqQueuedTime.isBefore(Instant.now().minus(60, ChronoUnit.SECONDS));
    }

    private void doPreliqOrder(PlaceOrderArgs placeOrderArgs, MarketServicePreliq marketService) throws InterruptedException {

        final BigDecimal block = placeOrderArgs.getAmount();
        final Long tradeId = placeOrderArgs.getTradeId();
        if (block.signum() <= 0) {
            String warn = "WARNING: block=" + block + ". No order on signal";
            Thread.sleep(1000);
            marketService.getTradeLogger().warn(warn);
            log.warn(warn);

            marketService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
            return;
        }

        slackNotifications.sendNotify(NotifyType.PRELIQ, String.format("%s %s a=%scont",
                placeOrderArgs.getSignalType().toString(),
                marketService.getName(),
                placeOrderArgs.getAmount()
        ));

        marketService.placeOrder(placeOrderArgs);
    }
}
