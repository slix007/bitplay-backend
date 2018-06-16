package com.bitplay.persistance;

import com.bitplay.Config;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.Delta;
import com.bitplay.persistance.repository.DeltaRepository;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.schedulers.ExecutorScheduler;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class DeltaRepositoryService {

    ObservableEmitter<Delta> emitter;
    private MongoOperations mongoOperation;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Scheduler scheduler = new ExecutorScheduler(executor);
    @Autowired
    private DeltaRepository deltaRepository;

    @Autowired
    private Config config;

    @Autowired
    private PersistenceService persistenceService;

    private volatile Delta lastSavedDelta;
    private Disposable savingListener;

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

    public void recreateSavingListener(BorderDelta borderDelta) {
        if (savingListener != null && !savingListener.isDisposed()) {
            savingListener.dispose();
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

    private void createAllDataSaving() {
        savingListener = createObservable()
                .subscribeOn(scheduler)
                .subscribe(this::doSave);
    }

    private void createDeviation(Integer deltaSaveDev) {
        if (deltaSaveDev == 0) {
            createAllDataSaving();
        } else {
            savingListener = createObservable()
                    .filter(delta -> isDeviationOvercome(delta, deltaSaveDev))
                    .subscribeOn(scheduler)
                    .subscribe(this::doSave);
        }
    }

    private Delta doSave(Delta delta) {
        lastSavedDelta = delta;
        return deltaRepository.save(delta);
    }

    private boolean isDeviationOvercome(Delta delta, Integer deltaSaveDev) {
        BigDecimal b_deltaCurr = delta.getbDelta();
        BigDecimal o_deltaCurr = delta.getoDelta();
        Delta lastSavedDelta = getLastSavedDelta();
        BigDecimal b_deltaLast = lastSavedDelta.getbDelta();
        BigDecimal o_deltaLast = lastSavedDelta.getoDelta();

        // abs(b_delta2) - abs(b_delta1) > delta_dev,
        // b_delta1 - последняя записанная в БД дельта, b_delta2 - текущая дельта
        boolean b = (b_deltaCurr.abs().subtract(b_deltaLast.abs())).compareTo(BigDecimal.valueOf(deltaSaveDev)) > 0;
        boolean o = (o_deltaCurr.abs().subtract(o_deltaLast.abs())).compareTo(BigDecimal.valueOf(deltaSaveDev)) > 0;
        return b || o;
    }

    private Delta getLastSavedDelta() {
        if (lastSavedDelta != null) {
            return lastSavedDelta;
        }
        lastSavedDelta = deltaRepository.findFirstByOrderByTimestampDesc();
        return lastSavedDelta;
    }

    private void createPeriodic(Integer deltaSavePerSec) {
        if (deltaSavePerSec == 0) {
            createAllDataSaving();
        } else {
            savingListener = createObservable()
                    .sample(deltaSavePerSec, TimeUnit.SECONDS)
                    .subscribeOn(scheduler)
                    .subscribe(this::doSave);
        }
    }

    private Observable<Delta> createObservable() {
        return Observable.create(emitter -> this.emitter = emitter);
    }

    public void add(Delta delta) {
        if (config.isDeltasSeriesEnabled()) {
            emitter.onNext(delta);
        }
    }

    public Stream<Delta> streamDeltas(Date from, Date to) {
        return deltaRepository.streamDeltasByTimestampBetween(from, to);
    }

    public Stream<Delta> streamDeltas(Integer count) {
        Pageable bottomPage = new PageRequest(0, count, Sort.Direction.DESC, "timestamp");
        final Date fromDate = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Page<Delta> byTimestampIsAfter = deltaRepository.findByTimestampIsAfter(fromDate, bottomPage);
        List<Delta> content = byTimestampIsAfter.getContent();

        return content.stream().sorted(Comparator.comparing(Delta::getTimestamp));
    }

}
