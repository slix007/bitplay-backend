package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.Dlt;
import com.bitplay.persistance.repository.DltRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class DeltaRepositoryService {

    private final Logger logger = LoggerFactory.getLogger(DeltaRepositoryService.class);

    private MongoOperations mongoOperation;
    @Autowired
    private DltRepository dltRepository;
    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private PersistenceService persistenceService;

    private volatile Dlt lastBtm;
    private volatile Dlt lastOk;
    private volatile Dlt lastSavedBtm;
    private volatile Dlt lastSavedOk;
    private Disposable savingListener;
    private Disposable savingListenerAdditionalForPeriodic;
    private Observable<Dlt> allDltObservable;
    private ObservableEmitter<Dlt> dltSavedEmitter;
    private Observable<Dlt> dltSaveObservable = Observable.create(e -> this.dltSavedEmitter = e);
    private Observable<Dlt> sharedDltSaveObservable = dltSaveObservable.share();


    @Autowired
    public DeltaRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    @PostConstruct
    private void init() {
        final String deltasSeriesName = "deltasSeries";
        if (!mongoOperation.collectionExists(deltasSeriesName)) {
            CollectionOptions options = new CollectionOptions(10 * 1000 * 1000, 600000, true);
            mongoOperation.createCollection(deltasSeriesName, options);
        }

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("delta-repository-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);

        allDltObservable = arbitrageService.getDeltaChangesPublisher()
                .observeOn(Schedulers.from(executor))
                .subscribeOn(Schedulers.from(executor))
                .flatMap(this::deltaChangeToDlt);

        initSettings();
    }

    private void initSettings() {
        BorderParams borderParams = persistenceService.fetchBorders();
        BorderDelta borderDelta = borderParams.getBorderDelta();
        if (borderDelta == null) {
            borderDelta = BorderDelta.createDefault();
            borderParams.setBorderDelta(borderDelta);
            persistenceService.saveBorderParams(borderParams);
        }

        recreateSavingListener(borderDelta);
    }

    private Observable<Dlt> deltaChangeToDlt(DeltaChange deltaChange) {
        List<Dlt> list = new ArrayList<>();
        if (deltaChange.getBtmDelta() != null) {
            Dlt delta = new Dlt(DeltaName.B_DELTA, new Date(), deltaChange.getBtmDelta().multiply(BigDecimal.valueOf(100)).longValue());
            list.add(delta);
            lastBtm = delta;
        }
        if (deltaChange.getOkDelta() != null) {
            Dlt delta = new Dlt(DeltaName.O_DELTA, new Date(), deltaChange.getOkDelta().multiply(BigDecimal.valueOf(100)).longValue());
            list.add(delta);
            lastOk = delta;
        }
        return Observable.fromIterable(list);
    }

    public void recreateSavingListener(BorderDelta borderDelta) {
        if (savingListener != null && !savingListener.isDisposed()) {
            savingListener.dispose();
        }
        if (savingListenerAdditionalForPeriodic != null && !savingListenerAdditionalForPeriodic.isDisposed()) {
            savingListenerAdditionalForPeriodic.dispose();
        }

        switch (borderDelta.getDeltaSaveType()) {
            case DEVIATION:
                createDeviation(borderDelta.getDeltaSaveDev());
                break;
            case PERIODIC:
                createPeriodic(borderDelta.getDeltaSavePerSec());
                break;
            case ALL:
                createAllDataSaving();
                break;
        }

    }

    private void createPeriodic(Integer deltaSavePerSec) {
        if (deltaSavePerSec == 0) {
            createAllDataSaving();
        } else {
            // Save nothing.
            // Just make work the chain of actions above.
            savingListener = allDltObservable.subscribe();

            // Save periodically
            savingListenerAdditionalForPeriodic = Observable
                    .interval(deltaSavePerSec, TimeUnit.SECONDS)
                    .subscribe(this::saveBoth);
        }
    }

    private void saveBoth(Long aLong) {
        try {
            if (lastBtm != null && lastOk != null) {
                Date currTime = new Date();
                Dlt btmDlt = new Dlt(DeltaName.B_DELTA, currTime, lastBtm.getValue());
                dltRepository.save(btmDlt);
                dltSavedEmitter.onNext(btmDlt);
                Dlt okDlt = new Dlt(DeltaName.O_DELTA, currTime, lastOk.getValue());
                dltRepository.save(okDlt);
                dltSavedEmitter.onNext(okDlt);
                lastSavedBtm = btmDlt;
                lastSavedOk = okDlt;
            } else {
                logger.error("Uninitialised lastDelta. lastBtm={}, lastOk={}", lastBtm, lastOk);
            }
        } catch (Exception e) {
            logger.error("Save both error", e);
        }
    }

    private void saveOne(Dlt dlt) {
        dltRepository.save(dlt);
        dltSavedEmitter.onNext(dlt);
        if (dlt.getName() == DeltaName.B_DELTA) {
            lastSavedBtm = dlt;
        } else {
            lastSavedOk = dlt;
        }
    }

    private void createAllDataSaving() {
        savingListener = allDltObservable
                .subscribe(this::saveOne);
    }

    private void createDeviation(Integer deltaSaveDev) {
        if (deltaSaveDev == 0) {
            createAllDataSaving();
        } else {
            savingListener = allDltObservable
                    .filter(delta -> isDeviationOvercome(delta, deltaSaveDev))
                    .subscribe(this::saveOne);
        }
    }

    private boolean isDeviationOvercome(Dlt dlt, Integer deltaSaveDev) {
        final Long deltaCurr = dlt.getValue();
        final Long deltaLast = getLastSavedDelta(dlt.getName()).getValue();

        // abs(b_delta2 - b_delta1) > delta_dev;
        // b_delta1 - последняя записанная в БД дельта, b_delta2 - текущая дельта
        final Long currDev = Math.abs(deltaCurr - deltaLast);
        final Long minDev = deltaSaveDev * 100L;
        return currDev >= minDev;
    }

    public Dlt getLastSavedDelta(DeltaName deltaName) {
        if (deltaName == DeltaName.B_DELTA && lastSavedBtm != null) {
            return lastSavedBtm;
        }
        if (deltaName == DeltaName.O_DELTA && lastSavedOk != null) {
            return lastSavedOk;
        }
        return dltRepository.findFirstByNameOrderByTimestampDesc(deltaName);
    }

    public Observable<Dlt> getDltSaveObservable() {
        return sharedDltSaveObservable;
    }

    public Stream<Dlt> streamDeltas(DeltaName deltaName, Date from, Date to) {
        return dltRepository.streamDltByNameAndTimestampBetween(deltaName, from, to);
    }

    public Stream<Dlt> streamDeltas(Date from, Date to) {
        return dltRepository.streamDltByTimestampBetween(from, to);
    }

    public Stream<Dlt> streamDeltas(DeltaName deltaName, Integer count) {
        Pageable bottomPage = new PageRequest(0, count, Sort.Direction.DESC, "timestamp");
        final Date fromDate = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Page<Dlt> byTimestampIsAfter = dltRepository.findByNameAndTimestampIsAfter(deltaName, fromDate, bottomPage);
        List<Dlt> content = byTimestampIsAfter.getContent();

        return content.stream().sorted(Comparator.comparing(Dlt::getTimestamp));
    }

    public Stream<Dlt> streamDeltas(Integer count) {
        Pageable bottomPage = new PageRequest(0, count, Sort.Direction.DESC, "timestamp");
        final Date fromDate = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Page<Dlt> byTimestampIsAfter = dltRepository.findByTimestampIsAfter(fromDate, bottomPage);
        List<Dlt> content = byTimestampIsAfter.getContent();

        return content.stream().sorted(Comparator.comparing(Dlt::getTimestamp));
    }

    public Dlt getIsBefore(DeltaName deltaName, Date timestamp) {
        Pageable bottomPage = new PageRequest(0, 1, Sort.Direction.DESC, "timestamp");
        Page<Dlt> byTimestampIsBefore = dltRepository.findByNameAndTimestampIsBefore(deltaName, timestamp, bottomPage);
        List<Dlt> content = byTimestampIsBefore.getContent();
        return content.size() > 0 ? content.get(0) : null;
    }
    public Dlt getIsAfter(DeltaName deltaName, Date timestamp) {
        Pageable bottomPage = new PageRequest(0, 1, Direction.ASC, "timestamp");
        Page<Dlt> pages = dltRepository.findByNameAndTimestampIsAfter(deltaName, timestamp, bottomPage);
        List<Dlt> content = pages.getContent();
        return content.size() > 0 ? content.get(0) : null;
    }
}
