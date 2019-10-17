package com.bitplay.persistance;

import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.CumTimeType;
import com.bitplay.persistance.domain.CumType;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

    private Query cumParamsQuery(TradingMode tradingMode) {
        if (tradingMode == null) {
            throw new IllegalArgumentException("TradingMode is null");
        }
        final Criteria criteriaTotal = Criteria.where("cumType").is(CumType.TOTAL);

        if (tradingMode == TradingMode.CURRENT || tradingMode == TradingMode.CURRENT_VOLATILE) {
            final Criteria criteriaCurrent = Criteria.where("cumType").is(CumType.CURRENT);
            return new Query().addCriteria(new Criteria().orOperator(criteriaTotal, criteriaCurrent));
        }
        final Criteria criteriaVolatile = Criteria.where("cumType").is(CumType.VOLATILE);
        return new Query().addCriteria(new Criteria().orOperator(criteriaTotal, criteriaVolatile));
    }

    public void incCounter1(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("vert1", 1), CumParams.class);
    }

    public void incCounter2(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("vert2", 1), CumParams.class);
    }

    public void incCompletedCounter1(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("completedVert1", 1), CumParams.class);
    }

    public void incCompletedCounter2(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("completedVert2", 1), CumParams.class);
    }

    public void incUnstartedVert1(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("unstartedVert1", 1), CumParams.class);
    }

    public void incUnstartedVert2(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("unstartedVert2", 1), CumParams.class);
    }

    public void incObRecheckUnstartedVert1(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("obRecheckUnstartedVert1", 1), CumParams.class);
    }

    public void incObRecheckUnstartedVert2(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("obRecheckUnstartedVert2", 1), CumParams.class);
    }

    public void incAbortedSignalUnstartedVert1(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("abortedSignalUnstartedVert1", 1), CumParams.class);
    }

    public void incAbortedSignalUnstartedVert2(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("abortedSignalUnstartedVert2", 1), CumParams.class);
    }

    public void addCumDelta(TradingMode tradingMode, BigDecimal deltaPlan) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("cumDelta", deltaPlan), CumParams.class);
    }

    public void addAstDelta1(TradingMode tradingMode, BigDecimal ast_delta1) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().set("astDelta1", ast_delta1).inc("cumAstDelta1", ast_delta1), CumParams.class);
    }

    public void addAstDelta2(TradingMode tradingMode, BigDecimal ast_delta2) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().set("astDelta2", ast_delta2).inc("cumAstDelta2", ast_delta2), CumParams.class);
    }

    public void addAstDeltaFact1(TradingMode tradingMode, BigDecimal ast_delta1_fact) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode),
                new Update().set("astDeltaFact1", ast_delta1_fact).inc("cumAstDeltaFact1", ast_delta1_fact), CumParams.class);
    }

    public void addAstDeltaFact2(TradingMode tradingMode, BigDecimal ast_delta2_fact) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode),
                new Update().set("astDeltaFact2", ast_delta2_fact).inc("cumAstDeltaFact2", ast_delta2_fact), CumParams.class);
    }

    public void addCumDiff2(TradingMode tradingMode, BigDecimal diff2_pre, BigDecimal diff2_post) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                        .inc("cumDiff2Pre", diff2_pre)
                        .inc("cumDiff2Post", diff2_post),
                CumParams.class);
    }

    public void addDeltaFact(TradingMode tradingMode, BigDecimal deltaFact) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("cumDeltaFact", deltaFact), CumParams.class);
    }

    public void addDiffFact(TradingMode tradingMode, BigDecimal diff_fact_v1_b, BigDecimal diff_fact_v1_o, BigDecimal diff_fact_v2) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                        // diff_fact_v1
                        .inc("cumDiffFact1", diff_fact_v1_b)
                        .inc("cumDiffFact2", diff_fact_v1_o)
                        // diff_fact_v2
                        .inc("cumDiffFact", diff_fact_v2),
                CumParams.class);
    }

    public void incDiffFactBrFailsCount(TradingMode tradingMode) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("diffFactBrFailsCount", 1), CumParams.class);
    }

    public void addDiffFactBr(TradingMode tradingMode, BigDecimal diffFactBr, boolean isEth) {
        int scale = isEth ? 3 : 2;
        final BigDecimal scaled = diffFactBr.setScale(scale, BigDecimal.ROUND_HALF_UP);
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update().inc("cumDiffFactBr", scaled), CumParams.class);
    }

    public void addAstDiffFact(TradingMode tradingMode, BigDecimal ast_diff_fact1, BigDecimal ast_diff_fact2, BigDecimal ast_diff_fact) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                .inc("cumAstDiffFact1", ast_diff_fact1)
                .inc("cumAstDiffFact2", ast_diff_fact2)
                .inc("cumAstDiffFact", ast_diff_fact), CumParams.class);
    }

    public void addCom(TradingMode tradingMode, BigDecimal com1, BigDecimal com2) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                .set("com1", com1)
                .set("com2", com2)
                .inc("cumCom1", com1)
                .inc("cumCom2", com2), CumParams.class);
    }

    public void addAstCom(TradingMode tradingMode, BigDecimal ast_com1, BigDecimal ast_com2, BigDecimal ast_com) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                .set("astCom1", ast_com1)
                .set("astCom2", ast_com2)
                .set("astCom", ast_com)
                .inc("cumAstCom1", ast_com1)
                .inc("cumAstCom2", ast_com2)
                .inc("cumAstCom", ast_com), CumParams.class);
    }

    public void addBitmexMCom(TradingMode tradingMode, BigDecimal bitmexMCom, BigDecimal ast_bitmex_m_com) {
        mongoTemplate.updateMulti(cumParamsQuery(tradingMode), new Update()
                .inc("cumBitmexMCom", bitmexMCom) // .setScale(2, BigDecimal.ROUND_HALF_UP)
                .set("astBitmexMCom", ast_bitmex_m_com)
                .inc("cumAstBitmexMCom", ast_bitmex_m_com), CumParams.class); // .setScale(8, BigDecimal.ROUND_HALF_UP)

    }

    public void setSlip(CumType cumType, CumTimeType cumTimeType, BigDecimal slipBr, BigDecimal slip) {
        final Criteria crCumType = Criteria.where("cumType").is(cumType);
        final Criteria crCumTimeType = Criteria.where("cumTimeType").is(cumTimeType);
        final Query query = new Query().addCriteria(new Criteria().andOperator(crCumType, crCumTimeType));
        mongoTemplate.updateFirst(query, new Update().set("slipBr", slipBr).set("slip", slip), CumParams.class);
    }
}

