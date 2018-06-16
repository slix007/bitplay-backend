package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Test;

public class DeltaWmaTest {

    @Test
    public void testSmaCalc() {
        DeltaWma deltaWma = new DeltaWma();
        deltaWma.addDelta(BigDecimal.valueOf(0), 1);// ignored, because first time filled.
        deltaWma.addDelta(BigDecimal.valueOf(5), 301);// timeWeight = 300
        deltaWma.addDelta(BigDecimal.valueOf(6), 1101);// timeWeight = 800

        BigDecimal theVal = deltaWma.getTheVal();
        System.out.println(theVal);
        Assert.assertEquals(BigDecimal.valueOf(5.72), theVal);
    }

}