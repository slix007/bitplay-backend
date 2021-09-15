package com.bitplay.okexv5.dto.request;

import java.lang.Character.Subset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class RequestDto {

    private final OP op;
    private final List<SubscriptionTopic> args;

    public RequestDto(OP op, List<String> args) {
        final List<SubscriptionTopic> topics = new ArrayList<>();
        for (String arg : args) {
            final String[] a = arg.split("/");
            topics.add(new SubscriptionTopic(a[0], a[1]));
        }
        this.args = topics;
        this.op = op;
    }

    public enum OP {
        subscribe, unsubscribe, login
    }

    @Data
    public static class SubscriptionTopic {

        private final String channel;
        private final String instId;
    }

    public static final String TICKERS = "tickers";
    public static final String INSTRUMENTS = "instruments";
    public static final String OPEN_INTEREST = "open-interest";
    public static final String TRADES = "trades";
    public static final String ESTIMATED_PRICE = "estimated-price";
    public static final String MARK_PRICE = "mark-price";
    public static final String INDEX_TICKERS = "index-tickers";
    public static final String PRICE_LIMIT = "price-limit";
    public static final String BOOKS = "books";
    public static final String BOOKS5 = "books5";
    public static final String BOOKS50_L2_TBT = "books50-l2-tbt";
    public static final String BOOKS_L2_TBT = "books-l2-tbt";
}
