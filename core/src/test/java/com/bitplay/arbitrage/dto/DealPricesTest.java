package com.bitplay.arbitrage.dto;

import static org.junit.Assert.*;

import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.borders.BordersV1;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Test;

public class DealPricesTest {

    @Test
    public void testSerializable() {
        DealPrices dealPrices = new DealPrices();
        dealPrices.setBorder1(BigDecimal.ONE);
        dealPrices.setBorder2(BigDecimal.ONE);
        dealPrices.setoBlock(BigDecimal.ONE);
        dealPrices.setbBlock(BigDecimal.ONE);
        dealPrices.setDelta1Plan(BigDecimal.ONE);
        dealPrices.setDelta2Plan(BigDecimal.ONE);
        dealPrices.setbPricePlan(BigDecimal.ONE);
        dealPrices.setoPricePlan(BigDecimal.ONE);
        dealPrices.setDeltaName(DeltaName.B_DELTA);
        dealPrices.setBestQuotes(new BestQuotes(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        dealPrices.setbPriceFact(new AvgPrice("", BigDecimal.ONE, "bitmex"));
        dealPrices.setoPriceFact(new AvgPrice("", BigDecimal.ONE, "okex"));

        dealPrices.setBorderParamsOnStart(new BorderParams(Ver.OFF, new BordersV1(), new BordersV2()));
        dealPrices.setPos_bo(1);
        dealPrices.calcPlanPosAo();

        DealPrices clone = SerializationUtils.clone(dealPrices);

        assertNotNull(clone);
    }
}