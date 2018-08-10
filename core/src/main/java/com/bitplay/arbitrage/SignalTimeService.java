package com.bitplay.arbitrage;

import com.bitplay.persistance.domain.SignalTimeParams;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SignalTimeService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public static final BigDecimal DEFAULT_VALUE = BigDecimal.valueOf(9999);

    public synchronized void addSignalTime(BigDecimal update) {
        SignalTimeParams signalTimeParams = fetchSignalTimeParams();
        if (update.compareTo(DEFAULT_VALUE) != 0) {
            if (signalTimeParams.getSignalTimeMax(). compareTo(DEFAULT_VALUE) == 0
                    || update.compareTo(signalTimeParams.getSignalTimeMax()) > 0) {
                signalTimeParams.setSignalTimeMax(update);
            }
            if (signalTimeParams.getSignalTimeMin().compareTo(DEFAULT_VALUE) == 0
                    || update.compareTo(signalTimeParams.getSignalTimeMin()) < 0) {
                signalTimeParams.setSignalTimeMin(update);
            }
            BigDecimal num = signalTimeParams.getAvgNum().add(update);
            BigDecimal den = signalTimeParams.getAvgDen().add(BigDecimal.ONE);
            signalTimeParams.setAvgNum(num);
            signalTimeParams.setAvgDen(den);
        }
        mongoTemplate.save(signalTimeParams);
    }

    public SignalTimeParams fetchSignalTimeParams() {
        return mongoTemplate.findById(3L, SignalTimeParams.class);
    }

    public synchronized void resetSignalTimeParams() {
        SignalTimeParams params = fetchSignalTimeParams();
        params.setSignalTimeMin(DEFAULT_VALUE);
        params.setSignalTimeMax(DEFAULT_VALUE);
        params.setAvgNum(BigDecimal.ZERO);
        params.setAvgDen(BigDecimal.ZERO);
        mongoTemplate.save(params);
    }

}
