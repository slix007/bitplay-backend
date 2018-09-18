package com.bitplay.persistance.domain.settings;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import org.junit.Test;

public class OkexContractTypeTest {

    @Test
    public void testThisWeek() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0831", OkexContractType.BTC_ThisWeek.getExpString(first));

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(7)));

        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(13)));
        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(16)));

        assertEquals("0921", OkexContractType.BTC_ThisWeek.getExpString(first.plusDays(21)));

        LocalDateTime second = LocalDateTime.of(2018, 9, 1, 7, 0, 0);

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(second));

        // the second of the change
        assertEquals("0831", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0)));
        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1)));

        assertEquals("0907", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 0)));
        assertEquals("0914", OkexContractType.BTC_ThisWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 1)));
    }

    @Test
    public void testNextWeek() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0907", OkexContractType.BTC_NextWeek.getExpString(first));

        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(first.plusDays(7)));

        // the second of the change
        assertEquals("0907", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0)));
        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1)));

        assertEquals("0914", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 0)));
        assertEquals("0921", OkexContractType.BTC_NextWeek.getExpString(LocalDateTime.of(2018, 9, 07, 8, 0, 1)));
    }

    @Test
    public void testQuorter() {
        LocalDateTime first = LocalDateTime.of(2018, 8, 28, 8, 0, 0);
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(first));

        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(first.plusDays(7)));

        // the second of the change
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 0)));
        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 8, 31, 8, 0, 1)));

        assertEquals("0928", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 14, 8, 0, 0)));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 14, 8, 0, 1)));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 18, 8, 0, 1)));

        // the change
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 28, 8, 0, 0)));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(LocalDateTime.of(2018, 9, 28, 8, 0, 1)));
    }

    @Test
    public void test1809Fix() {
        LocalDateTime first = LocalDateTime.of(2018, 9, 18, 17, 0, 0);
        assertEquals("0921", OkexContractType.BTC_ThisWeek.getExpString(first));
        assertEquals("0928", OkexContractType.BTC_NextWeek.getExpString(first));
        assertEquals("1228", OkexContractType.BTC_Quarter.getExpString(first));

    }
}