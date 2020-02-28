package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.market.MarketServicePortions;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.PlaceOrderArgs;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
@Service
public class PortionsListener {

    @Autowired
    private ArbitrageService arbitrageService;

    private Disposable readyEventListenerBtm;
    private Disposable readyEventListenerOk;

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {

        readyEventListenerBtm = readyListener(arbitrageService.getLeftMarketService());
        readyEventListenerOk = readyListener(arbitrageService.getRightMarketService());
    }

    private Disposable readyListener(MarketServicePortions marketService) {
        final ThreadFactory namedThreadFactory = new NamedThreadFactory("portions-" + marketService.getNameWithType());
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
                        log.error(marketService.getNameWithType() + " PortionsListener exception", e);
                    }
                }, throwable -> log.error("OkcoinPortionsListener", throwable));
    }

    public void check(MarketServicePortions marketService) {
        final PlaceOrderArgs poll = marketService.getPortionsQueue().poll();
        if (poll != null) {
            marketService.placeOrder(poll);
        }
    }


}
