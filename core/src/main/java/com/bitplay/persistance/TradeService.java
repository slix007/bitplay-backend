package com.bitplay.persistance;

import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.domain.fluent.LogLevel;
import com.bitplay.persistance.domain.fluent.LogRow;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.repository.FplayTradeRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Slf4j
@Service
public class TradeService {

    private static final String SEQ_NAME = "trade";

    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");

    private MongoOperations mongoOperation;

    @Autowired
    private SequenceDao sequenceDao;

    private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("deltas-log-%d").build());

    @Autowired
    private FplayTradeRepository fplayTradeRepository;

    @Autowired
    public TradeService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }


    public void info(long tradeId, String counterName, String theLog) {
        addLog(tradeId, counterName, LogLevel.INFO, theLog);
    }

    public void warn(long tradeId, String counterName, String theLog) {
        addLog(tradeId, counterName, LogLevel.WARN, theLog);
    }

    public void error(long tradeId, String counterName, String theLog) {
        addLog(tradeId, counterName, LogLevel.ERROR, theLog);
    }

    private synchronized void addLog(long tradeId, String counterName, LogLevel logLevel, String theLog) {
        deltasLogger.info(String.format("%s::%s %s", tradeId, counterName, theLog));

        executor.execute(() -> {
            FplayTrade toUpdate = getFplayTrade(tradeId, counterName, theLog);
            if (toUpdate == null) {
                return;
            }
            List<LogRow> deltaLog = toUpdate.getDeltaLog();
            deltaLog.add(new LogRow(logLevel, new Date(), theLog));
            toUpdate.setDeltaLog(deltaLog);

            fplayTradeRepository.save(toUpdate);
        });

    }

    private FplayTrade getFplayTrade(long tradeId, String counterName, String theLog) {
        FplayTrade toUpdate = getFplayTrade(tradeId);
        if (toUpdate == null) {
            return null;
        }

        if (!toUpdate.getCounterName().equals(counterName)) {
            log.warn("counterName={} is not match", counterName);
            log.warn(toUpdate.toString());
            log.warn(theLog);
        }
        return toUpdate;
    }

    private FplayTrade getFplayTrade(long tradeId) {
        FplayTrade toUpdate = fplayTradeRepository.findOne(tradeId);
        if (toUpdate == null) {
            log.warn("trade is null. Use the latest");
            toUpdate = fplayTradeRepository.findTopByOrderByDocumentIdDesc();
            log.warn("The latest trade is " + toUpdate);
            if (toUpdate == null) {
                return null;
            }
        }
        return toUpdate;
    }

    public synchronized void update(FplayTrade trade) {
        executor.submit(() -> {
            fplayTradeRepository.save(trade);
        });
    }

    public void setEndStatus(Long tradeId, TradeStatus tradeStatus) {
        executor.execute(() -> {
            FplayTrade toUpdate = getFplayTrade(tradeId);
            if (toUpdate == null) {
                return;
            }
            toUpdate.setTradeStatus(tradeStatus);

            fplayTradeRepository.save(toUpdate);
        });

    }

    public long createTrade(String counterName, DeltaName deltaName) {
        return createTrade(counterName, new Date(), deltaName);
    }

    public synchronized long createTrade(String counterName, Date startTimestamp, DeltaName deltaName) {
        final FplayTrade fplayTrade = new FplayTrade();
        fplayTrade.setCounterName(counterName);
        fplayTrade.setStartTimestamp(startTimestamp);
        fplayTrade.setDeltaName(deltaName);
        fplayTrade.setTradeStatus(TradeStatus.IN_PROGRESS);

        long nextId = sequenceDao.getNextSequenceId(SEQ_NAME);
        fplayTrade.setId(nextId);
        fplayTradeRepository.save(fplayTrade);
        return nextId;
    }
}
