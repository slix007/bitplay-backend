package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.arbitrage.exceptions.ToWarningLogException;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV1;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class DiffFactBrComputerTest {

    private BorderParams createDefaultBorders() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(70), 0, 0));
//        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(60), 500, 500));
//        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(50), 1000, 1000));
//        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(40), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(30), 10, 10));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(35), 20, 20));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 30, 30));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(45), 40, 40));
        borderBtmOpen.add(new BorderItem(5, BigDecimal.valueOf(50), 50, 50));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(5), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(0), 10, 10));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(-5), 20, 20));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-10), 30, 30));
        borderOkexClose.add(new BorderItem(5, BigDecimal.valueOf(-15), 40, 40));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(160), 0, 0));
//        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(150), 500, 500));
//        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(140), 1000, 1000));
//        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(130), 1500, 1500));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
    }

    @Test
    public void compute() {
        int pos_bo = 17;
        int pos_ao = 35;
        BigDecimal b_delta_plan = BigDecimal.valueOf(47);
        BigDecimal o_delta_plan = BigDecimal.ZERO;
        BigDecimal deltaFact = BigDecimal.valueOf(43);

        BorderParams borderParams = createDefaultBorders();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.OK_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "43 - ([0.17 * 35] + [0.56 * 40] + [0.28 * 45])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(2.05), diffFactBr.getVal());
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }


    private BorderParams createBordersBugOkOpenShort() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(70), 0, 0));
//        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(60), 500, 500));
//        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(50), 1000, 1000));
//        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(40), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
//        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(30), 10, 10));
//        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(35), 20, 20));
//        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 30, 30));
//        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(45), 40, 40));
//        borderBtmOpen.add(new BorderItem(5, BigDecimal.valueOf(50), 50, 50));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
//        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(5), 0, 0));
//        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(0), 10, 10));
//        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(-5), 20, 20));
//        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-10), 30, 30));
//        borderOkexClose.add(new BorderItem(5, BigDecimal.valueOf(-15), 40, 40));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
//        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(160), 0, 0));
//        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(150), 500, 500));
        borderOkexOpen.add(new BorderItem(7, BigDecimal.valueOf(30), 7000, 7000));
        borderOkexOpen.add(new BorderItem(8, BigDecimal.valueOf(30), 8000, 8000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
    }

    @Test
    public void computeBugOkOpenShort() {
        int pos_bo = -6000; // Delta2, okex sell, block=200
        int pos_ao = -6200;
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(37); // delta2_plan=37
        BigDecimal deltaFact = BigDecimal.valueOf(88.19);// delta2_fact=88.19

        BorderParams borderParams = createBordersBugOkOpenShort();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.OK_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "88.19 - ([1 * 30])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(58.19), diffFactBr.getVal());
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }


    private BorderParams createBordersBugOkOpenShort1() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(70), 0, 0));
//        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(60), 500, 500));
//        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(50), 1000, 1000));
//        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(40), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
//        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(30), 10, 10));
//        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(35), 20, 20));
//        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 30, 30));
//        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(45), 40, 40));
//        borderBtmOpen.add(new BorderItem(5, BigDecimal.valueOf(50), 50, 50));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
//        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(5), 0, 0));
//        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(0), 10, 10));
//        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(-5), 20, 20));
//        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-10), 30, 30));
//        borderOkexClose.add(new BorderItem(5, BigDecimal.valueOf(-15), 40, 40));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(15), 1000, 1000));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(15), 2000, 2000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(20), 3000, 3000));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(20), 4000, 4000));
        borderOkexOpen.add(new BorderItem(5, BigDecimal.valueOf(25), 5000, 5000));
        borderOkexOpen.add(new BorderItem(6, BigDecimal.valueOf(25), 6000, 6000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
    }

    @Test
    public void computeBugOkOpenShort1() {
        int pos_bo = -449; // Delta2, okex sell, block=1000
        int pos_ao = -1449;
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(29.52); // delta2_plan=37
        BigDecimal deltaFact = BigDecimal.valueOf(24.90);// delta2_fact=88.19

        BorderParams borderParams = createBordersBugOkOpenShort1();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.OK_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "88.19 - ([1 * 30])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(58.19), diffFactBr.getVal());
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

}