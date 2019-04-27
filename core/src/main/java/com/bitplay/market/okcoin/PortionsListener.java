package com.bitplay.market.okcoin;

import com.bitplay.market.MarketServicePortions;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.PlaceOrderArgs;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortionsListener {

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    private Disposable readyEventListenerBtm;
    private Disposable readyEventListenerOk;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {

        readyEventListenerBtm = readyListener(okCoinService);
        readyEventListenerOk = readyListener(bitmexService);
    }

    private Disposable readyListener(MarketServicePortions marketService) {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("portions-" + marketService.getName() + "-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);
        final Scheduler scheduler = Schedulers.from(executor);

        final EventBus eventBus = marketService.getEventBus();
        return eventBus.toObserverable()
                .subscribeOn(scheduler)
                .observeOn(scheduler)
                .subscribe(btsEventBox -> {
                    try {
                        if (!marketService.hasOpenOrders()) {
                            check(marketService);
                        }
                    } catch (Exception e) {
                        log.error(marketService.getName() + " PortionsListener exception", e);
                    }
                }, throwable -> log.error("OkcoinPortionsListener", throwable));
    }

    public void check(MarketServicePortions marketService) {
        final PlaceOrderArgs poll = marketService.getPortionsQueue().poll();
        if (poll != null) {
            if (marketService.getName().equals(BitmexService.NAME)) {
                ((BitmexService) marketService).placeOrderToOpenOrders(poll);
            } else {
                marketService.placeOrder(poll);
            }
        }
    }


}
