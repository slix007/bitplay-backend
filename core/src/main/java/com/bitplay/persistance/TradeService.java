package com.bitplay.persistance;

import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.domain.fluent.LogLevel;
import com.bitplay.persistance.domain.fluent.LogRow;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.repository.FplayTradeRepository;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

    private void addLog(long tradeId, String counterName, LogLevel logLevel, String theLog) {
        deltasLogger.info(String.format("%s::%s %s", tradeId, counterName, theLog));

        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .push("deltaLog", new LogRow(logLevel, new Date(), theLog))
                , FplayTrade.class);

    }

    public void setEndStatus(Long tradeId, TradeStatus tradeStatus) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .set("tradeStatus", tradeStatus),
                FplayTrade.class);
    }

    public FplayTrade createTrade(String counterName, DeltaName deltaName, BitmexContractType b, OkexContractType o) {
        return createTrade(counterName, new Date(), deltaName, b, o, TradeStatus.IN_PROGRESS);
    }

    private synchronized FplayTrade createTrade(String counterName, Date startTimestamp, DeltaName deltaName,
            BitmexContractType bitmexContractType, OkexContractType okexContractType, TradeStatus tradeStatus) {
        final FplayTrade fplayTrade = new FplayTrade();
        fplayTrade.setCounterName(counterName);
        fplayTrade.setVersion(0L);
        fplayTrade.setStartTimestamp(startTimestamp);
        fplayTrade.setDeltaName(deltaName);
        fplayTrade.setTradeStatus(tradeStatus);
        fplayTrade.setBitmexStatus(TradeMStatus.WAITING);
        fplayTrade.setOkexStatus(TradeMStatus.WAITING);
        fplayTrade.setBitmexContractType(bitmexContractType);
        fplayTrade.setOkexContractType(okexContractType);

        long nextId = sequenceDao.getNextSequenceId(SEQ_NAME);
        fplayTrade.setId(nextId);
        fplayTradeRepository.save(fplayTrade);

        return fplayTrade;
    }

    public void setBitmexStatus(Long tradeId, TradeMStatus status) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .set("bitmexStatus", status),
                FplayTrade.class);
    }

    public void setOkexStatus(Long tradeId, TradeMStatus status) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .set("okexStatus", status),
                FplayTrade.class);
    }

    private void addBitmexOrder(long tradeId, String bitmexOrderId) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .addToSet("bitmexOrders", bitmexOrderId)
                , FplayTrade.class);

    }

    private void addOkexOrder(long tradeId, String okexOrderId) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .addToSet("bitmexOrders", okexOrderId)
                , FplayTrade.class);

    }

}
