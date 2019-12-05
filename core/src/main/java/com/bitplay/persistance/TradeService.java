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
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.persistance.repository.FplayTradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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


    public void info(Long tradeId, String counterForLogs, String theLog) {
        addLog(tradeId, counterForLogs, LogLevel.INFO, theLog);
    }

    public void warn(Long tradeId, String counterForLogs, String theLog) {
        addLog(tradeId, counterForLogs, LogLevel.WARN, theLog);
    }

    public void error(Long tradeId, String counterForLogs, String theLog) {
        addLog(tradeId, counterForLogs, LogLevel.ERROR, theLog);
    }

    private void addLog(Long tradeId, String counterForLogs, LogLevel logLevel, String theLog) {
        deltasLogger.info(String.format("%s::%s %s", tradeId, counterForLogs, theLog));

        if (tradeId != null) {
            mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                    new Update()
                            .inc("version", 1)
                            .set("updated", new Date())
                            .push("deltaLog", new LogRow(logLevel, new Date(), theLog))
                    , FplayTrade.class);
        }

    }

    public void setTradingMode(Long tradeId, TradingMode tradingMode) {
        if (tradeId != null) {
            mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                    //.and("tradingMode").ne(TradingMode.CURRENT_VOLATILE)), // redundant?
                    new Update()
                            .inc("version", 1)
                            .set("updated", new Date())
                            .set("tradingMode", tradingMode),
                    FplayTrade.class);
        }
    }

    public void setEndStatus(Long tradeId, TradeStatus tradeStatus) {
        if (tradeId != null) {
            mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                    new Update()
                            .inc("version", 1)
                            .set("updated", new Date())
                            .push("tradeStatusUpdates", LocalDateTime.now().format(DateTimeFormatter.ISO_TIME) + " " + tradeStatus.toString())
                            .set("tradeStatus", tradeStatus),
                    FplayTrade.class);
        }
    }

    public FplayTrade createTrade(String counterName, DeltaName deltaName, BitmexContractType b, OkexContractType o) {
        return createTrade(counterName, new Date(), deltaName, b, o);
    }

    private FplayTrade createTrade(String counterName, Date startTimestamp, DeltaName deltaName,
            BitmexContractType bitmexContractType, OkexContractType okexContractType) {
        final FplayTrade fplayTrade = new FplayTrade();
        fplayTrade.setCounterName(counterName);
        fplayTrade.setVersion(0L);
        fplayTrade.setStartTimestamp(startTimestamp);
        fplayTrade.setDeltaName(deltaName);
        fplayTrade.setTradeStatus(TradeStatus.IN_PROGRESS);
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
        final Date dateNow = new Date();
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId).and("bitmexStatus").ne(TradeMStatus.NONE)),
                new Update()
                        .inc("version", 1)
                        .set("updated", dateNow)
                        .set("bitmexFinishTime", dateNow)
                        .push("bitmexStatusUpdates", LocalDateTime.now().format(DateTimeFormatter.ISO_TIME) + " " + status.toString())
                        .set("bitmexStatus", status),
                FplayTrade.class);
    }

    public void setOkexStatus(Long tradeId, TradeMStatus status) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId).and("okexStatus").ne(TradeMStatus.NONE)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .push("okexStatusUpdates", LocalDateTime.now().format(DateTimeFormatter.ISO_TIME) + " " + status.toString())
                        .set("okexStatus", status),
                FplayTrade.class);
    }

    public boolean isReadyToComplete(Long tradeId) {
        Query query = new Query(Criteria.where("_id").is(tradeId));
        query.fields().include("bitmexStatus");
        query.fields().include("okexStatus");
        query.fields().include("tradeStatus");
        final FplayTrade t = mongoOperation.findOne(query, FplayTrade.class);
        boolean bothCompleted = t.getBitmexStatus() == TradeMStatus.FINISHED && t.getOkexStatus() == TradeMStatus.FINISHED;
        boolean tradeStateInProgress = t.getTradeStatus() == TradeStatus.IN_PROGRESS;
        return bothCompleted && tradeStateInProgress;
    }

    public boolean isInProgress(Long tradeId) {
        Query query = new Query(Criteria.where("_id").is(tradeId));
        query.fields().include("tradeStatus");
        final FplayTrade t = mongoOperation.findOne(query, FplayTrade.class);
        return t.getTradeStatus() == TradeStatus.IN_PROGRESS;
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

    public void addBitmexPlacingMs(Long tradeId, long ms) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .addToSet("fplayTradeMon.bitmexPlacingMs", ms),
                FplayTrade.class);
    }

    public void addOkexPlacingMs(Long tradeId, long ms) {
        mongoOperation.updateFirst(new Query(Criteria.where("_id").is(tradeId)),
                new Update()
                        .inc("version", 1)
                        .set("updated", new Date())
                        .addToSet("fplayTradeMon.okexPlacingMs", ms),
                FplayTrade.class);
    }


    public Long getLastId() {
        final Long lastId = fplayTradeRepository.getLastId();
        if (lastId == null) {
            return 0L;
        }
        return lastId;
    }

    public FplayTrade getById(Long tradeId) {
        return fplayTradeRepository.findOne(tradeId);
    }

    public String getCounterName(Long tradeId) {
        final Query query = new Query(Criteria.where("_id").is(tradeId));
        query.fields().include("counterName");
        final FplayTrade t = mongoOperation.findOne(query, FplayTrade.class);
        return t != null ? t.getCounterName() : null;
    }


}
