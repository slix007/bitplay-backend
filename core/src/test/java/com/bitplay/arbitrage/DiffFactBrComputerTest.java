package com.bitplay.arbitrage;

import static com.bitplay.arbitrage.TestingMocks.*;

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

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
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

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
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

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
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

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "88.19 - ([1.00 * 30])", diffFactBr.getStr());
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

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void computeBugOkOpenShort1() {
        int pos_bo = -449; // Delta2, okex sell, block=1000
        int pos_ao = -1449;
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(29.52);
        BigDecimal deltaFact = BigDecimal.valueOf(24.90);

        BorderParams borderParams = createBordersBugOkOpenShort1();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "24.9 - ([0.55 * 15] + [0.45 * 15])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(990, 2), diffFactBr.getVal()); // 9.9
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

    private BorderParams createBordersBugOkClose() {
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
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(11), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(6), 10, 10));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(1), 20, 20));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-4), 30, 30));
        borderOkexClose.add(new BorderItem(5, BigDecimal.valueOf(-9), 40, 40));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
//        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(15), 1000, 1000));
//        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(15), 2000, 2000));
//        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(20), 3000, 3000));
//        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(20), 4000, 4000));
//        borderOkexOpen.add(new BorderItem(5, BigDecimal.valueOf(25), 5000, 5000));
//        borderOkexOpen.add(new BorderItem(6, BigDecimal.valueOf(25), 6000, 6000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void computeBugOkClose() {
        int pos_bo = 40; // Delta2, okex sell, block=10
        int pos_ao = 30;
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(-3.37);
        BigDecimal deltaFact = BigDecimal.valueOf(-8.37);

        BorderParams borderParams = createBordersBugOkClose();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "-8.37 - ([1.00 * -4])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(-4.37), diffFactBr.getVal());
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

    @Test
    public void computeBugOkClose2() {
        int pos_bo = 29; // Delta2, okex sell, block=10
        int pos_ao = 12;
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(6);
        BigDecimal deltaFact = BigDecimal.valueOf(-8.37);

        BorderParams borderParams = createBordersBugOkClose();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "-8.37 - ([0.47 * 6] + [0.53 * 1])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(-11.72), diffFactBr.getVal());
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }


    private BorderParams createBordersBugWamBr0() {
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
        // {id=3,val=24,pLL=30,pSL=30},m=10,b=198;{id=4,val=29,pLL=40,pSL=40},m=20,b=11;{id=5,val=34,pLL=50,pSL=50},m=30,b=10', deltaVal='35.0'} ",
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(14), 10, 10));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(19), 20, 20));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(24), 30, 30));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(29), 40, 40));
        borderOkexOpen.add(new BorderItem(5, BigDecimal.valueOf(34), 50, 50));
//        borderOkexOpen.add(new BorderItem(6, BigDecimal.valueOf(25), 6000, 6000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void computeBugWamBr0() {
        // Error: wam_br=0. pos_bo=-20, pos_ao=-26, pos_mode=OK_MODE,
        // delta_plan=35.0,
        // bordersTable=Optional[
        // {id=1,val=14,pLL=10,pSL=10},
        // {id=2,val=19,pLL=20,pSL=20},
        // {id=3,val=24,pLL=30,pSL=30},
        // {id=4,val=29,pLL=40,pSL=40},
        // {id=5,val=34,pLL=50,pSL=50},
        // {id=0,val=null,pLL=60,pSL=60}, {id=0,val=null,pLL=70,pSL=70}, {id=0,val=null,pLL=80,pSL=80}, {id=0,val=null,pLL=90,pSL=90}, {id=0,val=null,pLL=100,pSL=100}, {id=0,val=null,pLL=0,pSL=0}] ",

        int pos_bo = -20; // Delta2, okex sell, block=6
        int pos_ao = -26; // Pos diff: b(+1300) o(+0-20) = -700, ha=-700, dc=0, mdc=11000
        BigDecimal b_delta_plan = BigDecimal.ZERO;
        BigDecimal o_delta_plan = BigDecimal.valueOf(35);
        BigDecimal deltaFact = BigDecimal.valueOf(32.43);

        BorderParams borderParams = createBordersBugWamBr0();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "32.43 - ([1.00 * 24])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(8.43), diffFactBr.getVal()); // 9.9
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

    private BorderParams createBordersDeltaPlanZero() {
        // borderName='b_br_close',
        // borderValue=';{id=2,val=0,pLL=10,pSL=10},m=7,b=101', deltaVal='3.66'} "
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(5), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(0), 10, 10));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(-5), 20, 20));
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
        // {id=3,val=24,pLL=30,pSL=30},m=10,b=198;{id=4,val=29,pLL=40,pSL=40},m=20,b=11;{id=5,val=34,pLL=50,pSL=50},m=30,b=10', deltaVal='35.0'} ",
//        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(14), 10, 10));
//        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(19), 20, 20));
//        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(24), 30, 30));
//        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(29), 40, 40));
//        borderOkexOpen.add(new BorderItem(5, BigDecimal.valueOf(34), 50, 50));
//        borderOkexOpen.add(new BorderItem(6, BigDecimal.valueOf(25), 6000, 6000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void testDeltaPlanZero() {
        // 19:35:58.962 #19 delta1=6274.5-6270.84=3.66;
        // borderV2:TradingSignal{tradeType=DELTA1_B_SELL_O_BUY,
        // bitmexBlock=700, okexBlock=7, ver=DYNAMIC, posMode=OK_MODE,
        // borderName='b_br_close',
        // borderValue=';{id=2,val=0,pLL=10,pSL=10},m=7,b=101', deltaVal='3.66'} "
        //
        // "19:35:58.963 #19 Pos diff: b(+1250) o(+0-17) = -450, ha=-450, dc=0, mdc=11000 ",
        int pos_bo = -17; // Delta2, okex buy, block=7
        int pos_ao = -10;
        BigDecimal b_delta_plan = BigDecimal.valueOf(3.36);
        BigDecimal o_delta_plan = BigDecimal.ZERO;
        BigDecimal deltaFact = BigDecimal.valueOf(3.16); // delta1_fact=6274.00-6270.84=3.16;

        BorderParams borderParams = createBordersDeltaPlanZero();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "3.16 - ([1.00 * 0])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(3.16), diffFactBr.getVal()); // 9.9
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

    @Test
    public void testDeltaPlanZero1() {

        int pos_bo = -16; // Delta2, okex buy, block=10
        int pos_ao = -6;
        BigDecimal b_delta_plan = BigDecimal.valueOf(6.11);
        BigDecimal o_delta_plan = BigDecimal.ZERO;
        BigDecimal deltaFact = BigDecimal.valueOf(-17.25);

        BorderParams borderParams = createBordersDeltaPlanZero();
        BordersV2 bordersV2 = borderParams.getBordersV2();

        DiffFactBrComputer diffFactBrComputer = new DiffFactBrComputer(PosMode.RIGHT_MODE, pos_bo, pos_ao,
                b_delta_plan,
                o_delta_plan,
                deltaFact, bordersV2);

        try {
            DiffFactBr diffFactBr = diffFactBrComputer.compute();

//            System.out.println(diffFactBr.getStr());
            Assert.assertEquals("diffFactBrString", "-17.25 - ([0.40 * 5] + [0.60 * 0])", diffFactBr.getStr());
            Assert.assertEquals("diffFactBr", BigDecimal.valueOf(-19.25), diffFactBr.getVal()); // 9.9
        } catch (ToWarningLogException e) {
            e.printStackTrace();
            Assert.fail("No errors expected");
        }
    }

}
