package com.bitplay.persistance;

import com.bitplay.persistance.domain.mon.MonMoving;
import com.bitplay.persistance.domain.mon.MonRestart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MonitoringDataService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public MonRestart fetchRestartMonitoring() {
        MonRestart firstByDocumentId = mongoTemplate.findById(1L, MonRestart.class);
        if (firstByDocumentId == null) {
            firstByDocumentId = MonRestart.createDefaults();
            firstByDocumentId = saveRestartMonitoring(firstByDocumentId);
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

    public MonMoving fetchMonMoving() {
        MonMoving doc = mongoTemplate.findById(2L, MonMoving.class);
        if (doc == null || doc.getAfter() == null) {
            doc = MonMoving.createDefaults();
            doc = saveMonMoving(doc);
        }
        return doc;
    }

    public MonMoving saveMonMoving(MonMoving monMoving) {
        if (monMoving.getId() == null) {
            monMoving.setId(2L);
        }
        mongoTemplate.save(monMoving);
        return monMoving;
    }

}
