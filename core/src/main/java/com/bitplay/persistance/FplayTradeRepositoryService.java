package com.bitplay.persistance;

import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.repository.FplayTradeRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class FplayTradeRepositoryService {

    private static final String SEQ_NAME = "trade";

    private MongoOperations mongoOperation;

    @Autowired
    private SequenceDao sequenceDao;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Autowired
    private FplayTradeRepository fplayTradeRepository;

    @Autowired
    public FplayTradeRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    public synchronized void updateOrInsert(FplayTrade trade) {
        executor.submit(() -> {
            //TODO
//            if (trade.getId() != null) {
//                FplayTrade toUpdate = fplayTradeRepository.findOne(trade.getId());
//                toUpdate.getBitmexOrders().replaceAll();

//            }
            fplayTradeRepository.save(trade);
        });
    }


    public synchronized void update(FplayTrade trade) {
        executor.submit(() -> {
            fplayTradeRepository.save(trade);
        });
    }

    public synchronized void insert(FplayTrade fplayTrade) {
        executor.submit(() -> {
            long nextId = sequenceDao.getNextSequenceId(SEQ_NAME);
            fplayTrade.setId(nextId);
            fplayTradeRepository.save(fplayTrade);
        });
    }
}
