package com.bitplay.market.events;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
public class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final Subject<BtsEventBox> _bus = PublishSubject.create();

    public void send(BtsEventBox o) {
        _bus.onNext(o);
    }

    public Observable<BtsEventBox> toObserverable() {
        return _bus
                .doOnError(throwable -> logger.error("EventBus.", throwable))
                .retry();
    }
}
