package com.bitplay.persistance;

import com.bitplay.Config;
import com.bitplay.persistance.domain.fluent.Delta;
import com.bitplay.persistance.repository.DeltaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.internal.schedulers.ExecutorScheduler;

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
        defferedSaver();
    }

    private Observable<Delta> createObservable() {
        return Observable.create(emitter -> this.emitter = emitter);
    }

    private void defferedSaver() {
        createObservable()
                .sample(1, TimeUnit.MINUTES)
                .subscribeOn(scheduler)
                .subscribe(delta -> deltaRepository.save(delta));
    }

    public void add(Delta delta) {
        if (config.isDeltasSeriesEnabled()) {
            emitter.onNext(delta);
        }
    }

    public Stream<Delta> streamDeltas(Date from, Date to) {
        return deltaRepository.streamDeltasByTimestampBetween(from, to);
    }


//        executor.submit(() -> {
//            deltaRepository.save(delta);
//        });
}
