package com.bitplay.persistance;

import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.CumTimeType;
import com.bitplay.persistance.domain.CumType;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Id to cumulative-block
 * <ul>
 * <li>10 - total common</li>
 * <li>11 - total extended</li>
 * <li>12 - current common</li>
 * <li>13 - current extended</li>
 * <li>14 - volatile common</li>
 * <li>15 - volatile extended</li>
 * </ul>
 */
@Service
public class CumPersistenceService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public CumParams fetchCum(CumType type, CumTimeType timeType) {
        long id = 0;
        if (type == CumType.TOTAL) {
            if (timeType == CumTimeType.COMMON) {
                id = 10;
            } else {
                id = 11;
            }
        } else if (type == CumType.CURRENT) {
            if (timeType == CumTimeType.COMMON) {
                id = 12;
            } else {
                id = 13;
            }
        } else if (type == CumType.VOLATILE) {
            if (timeType == CumTimeType.COMMON) {
                id = 14;
            } else {
                id = 15;
            }
        }
        if (id == 0) {
            throw new IllegalArgumentException("no CumParams for " + type + ", " + timeType);
        }

        return mongoTemplate.findById(id, CumParams.class);
    }

    public void saveCumParams(CumParams cumParams) {
        mongoTemplate.save(cumParams);
    }

    public List<CumParams> fetchAllCum() {
        Query query = new Query().addCriteria(Criteria.where("cumType").exists(true));
        return mongoTemplate.find(query, CumParams.class);
    }
}
