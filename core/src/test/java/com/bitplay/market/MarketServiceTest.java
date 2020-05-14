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

        final BigDecimal upExact0 = marketService.setScaleUp(BigDecimal.valueOf(7809.5), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(7809.5), upExact0);

        // scale 1, ticksize 0.5
        final BigDecimal upExact = marketService.setScaleUp(BigDecimal.valueOf(7809.02), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(7809.5), upExact);

        final BigDecimal upHalf = marketService.setScaleUp(BigDecimal.valueOf(7809.62), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(7810.0), upHalf);

        final BigDecimal diff = marketService.setScaleUp(BigDecimal.valueOf(0.02), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(0.5), diff);

        final BigDecimal diff1 = marketService.setScaleUp(BigDecimal.valueOf(0.92), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(1.0), diff1);

        final BigDecimal diff2 = marketService.setScaleUp(BigDecimal.valueOf(0.49992), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(0.5), diff2);

        final BigDecimal diff3 = marketService.setScaleUp(BigDecimal.valueOf(0.500001), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(1.0), diff3);

        final BigDecimal diff4 = marketService.setScaleUp(BigDecimal.valueOf(-0.2), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(-0.5), diff4);

        final BigDecimal diff5 = marketService.setScaleUp(BigDecimal.valueOf(-0.07), BitmexContractType.ETHUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-0.10).setScale(2), diff5);

        final BigDecimal diff6 = marketService.setScaleUp(BigDecimal.valueOf(-0.04), BitmexContractType.ETHUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(-0.05), diff6);

        final BigDecimal diff7 = marketService.setScaleUp(BigDecimal.valueOf(-0.04), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(-0.5), diff7);
    }

    @Test
    public void setScaleDown() {
        final MarketService marketService = new BitmexService();

        final BigDecimal upExact0 = marketService.setScaleDown(BigDecimal.valueOf(7809.5), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(7809.5), upExact0);

        // scale 1, ticksize 0.5
        final BigDecimal upExact = marketService.setScaleDown(BigDecimal.valueOf(7809.02), BitmexContractType.XBTUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(7809.0).setScale(1), upExact);

        final BigDecimal upHalf = marketService.setScaleDown(BigDecimal.valueOf(7809.62), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(7809.5), upHalf);

        final BigDecimal diff = marketService.setScaleDown(BigDecimal.valueOf(0.02), BitmexContractType.XBTUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(0.0).setScale(1), diff);

        final BigDecimal diff1 = marketService.setScaleDown(BigDecimal.valueOf(0.92), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(0.5), diff1);

        final BigDecimal diff2 = marketService.setScaleDown(BigDecimal.valueOf(0.49992), BitmexContractType.XBTUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(0.0).setScale(1), diff2);

        final BigDecimal diff3 = marketService.setScaleDown(BigDecimal.valueOf(0.500001), BitmexContractType.XBTUSD_Perpetual);
        assertEquals(BigDecimal.valueOf(0.5), diff3);

        final BigDecimal diff4 = marketService.setScaleDown(BigDecimal.valueOf(-0.2), BitmexContractType.XBTUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-0.0).setScale(1), diff4);

        final BigDecimal diff5 = marketService.setScaleDown(BigDecimal.valueOf(-0.07), BitmexContractType.ETHUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-0.05).setScale(2), diff5);

        final BigDecimal diff6 = marketService.setScaleDown(BigDecimal.valueOf(-0.04), BitmexContractType.ETHUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-0.00).setScale(2), diff6);

        final BigDecimal diff7 = marketService.setScaleDown(BigDecimal.valueOf(-1.07), BitmexContractType.XBTUSD_Perpetual);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        assertEquals(BigDecimal.valueOf(-1.0).setScale(1), diff7);
    }
}
