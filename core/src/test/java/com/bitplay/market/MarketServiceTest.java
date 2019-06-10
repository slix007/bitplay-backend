package com.bitplay.market;

import static org.junit.Assert.assertEquals;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import java.math.BigDecimal;
import org.junit.Test;

public class MarketServiceTest {

    @Test
    public void setScale() {
        final MarketService marketService = new BitmexService();

        final BigDecimal upExact0 = marketService.setScaleUp(BigDecimal.valueOf(7809.5), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(7809.5), upExact0);

        // scale 1, ticksize 0.5
        final BigDecimal upExact = marketService.setScaleUp(BigDecimal.valueOf(7809.02), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(7809.5), upExact);

        final BigDecimal upHalf = marketService.setScaleUp(BigDecimal.valueOf(7809.62), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(7810.0), upHalf);


        final BigDecimal diff = marketService.setScaleUp(BigDecimal.valueOf(0.02), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(0.5), diff);

        final BigDecimal diff1 = marketService.setScaleUp(BigDecimal.valueOf(0.92), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(1.0), diff1);

        final BigDecimal diff2 = marketService.setScaleUp(BigDecimal.valueOf(0.49992), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(0.5), diff2);

        final BigDecimal diff3 = marketService.setScaleUp(BigDecimal.valueOf(0.500001), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(1.0), diff3);

        final BigDecimal diff4 = marketService.setScaleUp(BigDecimal.valueOf(-0.2), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(-0.5), diff4);

        final BigDecimal diff5 = marketService.setScaleUp(BigDecimal.valueOf(-0.07), BitmexContractType.ETHUSD);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-0.10).setScale(2), diff5);

        final BigDecimal diff6 = marketService.setScaleUp(BigDecimal.valueOf(-0.04), BitmexContractType.ETHUSD);
        assertEquals(BigDecimal.valueOf(-0.05), diff6);

        final BigDecimal diff7 = marketService.setScaleUp(BigDecimal.valueOf(-0.04), BitmexContractType.XBTUSD);
        assertEquals(BigDecimal.valueOf(-0.5), diff7);
    }
}