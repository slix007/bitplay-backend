package com.bitplay.utils;

import org.knowm.xchange.dto.trade.LimitOrder;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
public class Utils {

    public static List<LimitOrder> getBestBids(List<LimitOrder> bids, int amount) {
        return bids.stream()
                .sorted((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()))
                .limit(amount)
                .collect(Collectors.toList());
    }

    public static List<LimitOrder> getBestAsks(List<LimitOrder> asks, int amount) {
        return asks.stream()
                .sorted(Comparator.comparing(LimitOrder::getLimitPrice))
                .limit(amount)
                .collect(Collectors.toList());
    }

}
