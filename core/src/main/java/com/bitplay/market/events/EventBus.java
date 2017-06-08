package com.bitplay.market.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
public class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final Subject<BtsEvent> _bus = PublishSubject.create();

    public void send(BtsEvent o) {
        _bus.onNext(o);
    }

    public Observable<BtsEvent> toObserverable() {
        return _bus
                .doOnError(throwable -> logger.error("EventBus.", throwable))
                .retry();
    }
}
