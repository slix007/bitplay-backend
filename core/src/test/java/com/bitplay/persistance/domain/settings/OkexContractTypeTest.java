package com.bitplay.persistance.domain.settings;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class OkexContractTypeTest {

    @Test
    public void testThisWeek() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0831", OkexContractType.BTC_ThisWeek.getExpString(first, false));

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(7), false));

        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(13), false));
        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(16), false));

        assertEquals("0921", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(21), false));

        LocalDateTime second = LocalDateTime.of(2018, 9, 1, 7, 0, 0);

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(second, false));

        // the second of the change
        assertEquals("0831", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0), false));
        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1), false));

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 0), false));
        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 1), false));
    }

    @Test
    public void testNextWeek() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0907", OkexContractType.BTC_NextWeek.getExpString(first, false));

        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(first.plusDays(7), false));

        // the second of the change
        assertEquals("0907", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0), false));
        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1), false));

        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 0), false));
        assertEquals("0921", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 1), false));
    }

    @Test
    public void testQuorter() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(first, false));

        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(first.plusDays(7), false));

        // the second of the change
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0), false));
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1), false));

        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 14, 8, 0, 0), false));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 14, 8, 0, 1), false));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 18, 8, 0, 1), false));

        // the change
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 28, 8, 0, 0), false));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 28, 8, 0, 1), false));
    }

    @Test
    public void testBiQuorter() {
        LocalDateTime nowDate = LocalDateTime.of(2020, 3, 12, 8, 0, 1);
        assertEquals("0313", OkexContractType.BTC_ThisWeek.getExpString(nowDate, false));
        assertEquals("0320", OkexContractType.BTC_NextWeek.getExpString(nowDate, false));
        assertEquals("0327", OkexContractType.BTC_Quarter.getExpString(nowDate, false));

        assertEquals("0626", OkexContractType.BTC_BiQuarter.getExpString(nowDate, false));
        assertEquals("0626", OkexContractType.ETH_BiQuarter.getExpString(nowDate, false));

        final LocalDateTime nowDate2 = LocalDateTime.of(2020, 3, 13, 8, 0, 1);
        assertEquals("0626", OkexContractType.BTC_Quarter.getExpString(nowDate2, false));
        assertEquals("0626", OkexContractType.ETH_Quarter.getExpString(nowDate2, false));
        assertEquals("0925", OkexContractType.BTC_BiQuarter.getExpString(nowDate2, false));
        assertEquals("0925", OkexContractType.ETH_BiQuarter.getExpString(nowDate2, false));

        final LocalDateTime nowDate3 = LocalDateTime.of(2020, 6, 27, 8, 0, 1);
        assertEquals("0925", OkexContractType.BTC_Quarter.getExpString(nowDate3, false));
        assertEquals("0925", OkexContractType.ETH_Quarter.getExpString(nowDate3, false));
        assertEquals("1225", OkexContractType.BTC_BiQuarter.getExpString(nowDate3, false));
        assertEquals("1225", OkexContractType.ETH_BiQuarter.getExpString(nowDate3, false));


        final LocalDateTime nowDate4 = LocalDateTime.of(2021, 6, 12, 8, 0, 1);
        assertEquals("0924", OkexContractType.ETH_Quarter.getExpString(nowDate4, false));
        assertEquals("0924", OkexContractType.BTC_Quarter.getExpString(nowDate4, false));
        assertEquals("1231", OkexContractType.BTC_BiQuarter.getExpString(nowDate4, false));
        assertEquals("1231", OkexContractType.ETH_BiQuarter.getExpString(nowDate4, false));

    }

    @Test
    public void test1809Fix() {
        LocalDateTime first = LocalDateTime.of(2018, 9, 18, 17, 0, 0);
        assertEquals("0921", OkexContractType.BTC_ThisWeek.getExpString(first, false));
        assertEquals("0928", OkexContractType.BTC_NextWeek.getExpString(first, false));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(first, false));

    }

    @Test
    public void testLambdaStreamList() {
        final List<String> list = new ArrayList<>();
        list.add("3");
        list.add("5");
        list.add("4");
        list.add("2");
        list.add("5");
        final Stream<String> stream1 = list.stream();
        final Stream<String> stream2 = list.stream();
        final List<String> list2 = stream1
//                .map(String::new)
                .collect(Collectors.toList());
//        final List<Integer> list2 = new ArrayList<>(list);

        System.out.println(Integer.toHexString(list.hashCode()));
        System.out.println(Integer.toHexString(list2.hashCode()));

        System.out.println("list=" + System.identityHashCode(list));
        System.out.println("list2=" + System.identityHashCode(list2));
        System.out.println("list[0]=" + System.identityHashCode(list.get(0)));
        System.out.println("list2[0]=" + System.identityHashCode(list2.get(0)));
        System.out.println(list.get(0) == list2.get(0));
        System.out.println(list == list2);
        System.out.println("stream1=" + System.identityHashCode(stream1));
        System.out.println("stream2=" + System.identityHashCode(stream2));



    }
}
