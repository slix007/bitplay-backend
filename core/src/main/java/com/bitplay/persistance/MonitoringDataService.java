package com.bitplay.persistance;

import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.mon.Mon;
import com.bitplay.persistance.domain.mon.MonObTimestamp;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.bitplay.persistance.exception.SequenceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MonitoringDataService {

    private static final String SEQ_NAME = "mon";

    @Autowired
    private SequenceDao sequenceDao;
    @Autowired
    private MongoTemplate mongoTemplate;

    public MonRestart fetchRestartMonitoring() {
        MonRestart m = mongoTemplate.findById(1L, MonRestart.class);
        if (m == null || m.getBTimestampDelayMax() == null || m.getOTimestampDelayMax() == null) {
            m = MonRestart.createDefaults();
            m = saveRestartMonitoring(m);
        }
        return m;
    }

    public MonObTimestamp fetchTimestampMonitoring(String marketName) {
        MonObTimestamp firstByDocumentId = findMonTimestamp(marketName);
        if (firstByDocumentId == null) {
            firstByDocumentId = MonObTimestamp.createDefaults(marketName);
            firstByDocumentId = saveMonTimestamp(firstByDocumentId);
        }
        return firstByDocumentId;
    }


    public MonRestart saveRestartMonitoring(MonRestart monRestart) {
        if (monRestart.getId() == null) {
            monRestart.setId(1L);
        }
        mongoTemplate.save(monRestart);
        return monRestart;
    }
//
//    public Mon fetchMonMoving() {
//        Mon doc = mongoTemplate.findById(2L, Mon.class);
//        if (doc == null || doc.getAfter() == null) {
//            doc = Mon.createDefaults();
//            doc = saveMonMoving(doc);
//        }
//        return doc;
//    }
//
//    public Mon saveMonMoving(Mon monMoving) {
//        if (monMoving.getId() == null) {
//            monMoving.setId(2L);
//        }
//        mongoTemplate.save(monMoving);
//        return monMoving;
//    }

    public Mon fetchMon(String marketName, String typeName) {
        Mon doc = findMon(marketName, typeName);
        if (doc == null || doc.getAfter() == null) {
            doc = Mon.createDefaults();
            doc.setMarketName(marketName);
            doc.setTypeName(typeName);

            long nextId = sequenceDao.getNextSequenceId(SEQ_NAME);
            doc.setId(nextId);
            doc = saveMon(doc);
        }
        return doc;
    }

    public Mon saveMon(Mon mon) {
        if (mon.getId() == null) {
            Mon exists = findMon(mon.getMarketName(), mon.getTypeName());
            if (exists == null) {
                throw new SequenceException("Unable to find mon: " + mon);
            }
            mon.setId(exists.getId());
        }
        mongoTemplate.save(mon);
        return mon;
    }

    private Mon findMon(String marketName, String typeName) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("marketName").is(marketName)
                        .and("typeName").is(typeName)),
                Mon.class);
    }


    public MonObTimestamp saveMonTimestamp(MonObTimestamp mon) {
        if (mon.getId() == null) {
            long nextId = sequenceDao.getNextSequenceId(SEQ_NAME);
            mon.setId(nextId);
        }
        mongoTemplate.save(mon);
        return mon;
    }

    private MonObTimestamp findMonTimestamp(String marketName) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("marketName").is(marketName)
                        .and("_class").is("monObTimestamp")
                ), MonObTimestamp.class);
    }


}
