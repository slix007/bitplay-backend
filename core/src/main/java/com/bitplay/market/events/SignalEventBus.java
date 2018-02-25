package com.bitplay.market.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
public class SignalEventBus {

    private static final Logger logger = LoggerFactory.getLogger(SignalEventBus.class);
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    private final Subject<SignalEvent> _bus = PublishSubject.create();

    public void send(SignalEvent o) {
        _bus.onNext(o);
    }

    public Observable<SignalEvent> toObserverable() {
        return _bus
                .doOnError(throwable -> logger.error("SignalEventBus.", throwable))
                .retry()
                .share()
                .doOnTerminate(() -> logger.info("SignalEventBus has been terminated."));
//                .doOnEach(notification -> debugLog.debug("SignalEventBus:" + (notification != null ? notification.toString() : "null")));
    }
}
