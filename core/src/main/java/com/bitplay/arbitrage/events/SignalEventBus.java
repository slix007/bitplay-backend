package com.bitplay.arbitrage.events;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
public class SignalEventBus {

    private static final Logger logger = LoggerFactory.getLogger(SignalEventBus.class);

    private final Subject<EventQuant> _bus = PublishSubject.create();

    public void send(EventQuant o) {
        _bus.onNext(o);
    }

    public Observable<EventQuant> toObserverable() {
        return _bus
                .doOnError(throwable -> logger.error("SignalEventBus.", throwable))
                .retry()
                .share()
                .doOnTerminate(() -> logger.info("SignalEventBus has been terminated."));
//                .doOnEach(notification -> debugLog.debug("SignalEventBus:" + (notification != null ? notification.toString() : "null")));
    }
}
