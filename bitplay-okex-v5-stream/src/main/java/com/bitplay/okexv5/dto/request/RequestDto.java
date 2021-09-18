package com.bitplay.okexv5.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RequestDto {

    protected final OP op;
    protected final List<Args> args;

    /**
     * Used by Public channels
     */
    public RequestDto(OP op, List<String> args) {
        final List<Args> topics = new ArrayList<>();
        for (String arg : args) {
            final String[] a = arg.split("/");
            topics.add(new SubscriptionTopic(a[0], a[1]));
        }
        this.args = topics;
        this.op = op;
    }

    /**
     * Used by Private channels
     */
    public RequestDto(OP op, Args args) {
        this.op = op;
        final List<Args> argsList = new ArrayList<>();
        argsList.add(args);
        this.args = argsList;
    }

    public static RequestDto loginRequestDto(String apiKey, String passphrase, String timestamp, String sign) {
        return new RequestDto(OP.login, new LoginRequestArgs(apiKey, passphrase, timestamp, sign));
    }

    public enum OP {
        subscribe, unsubscribe, login
    }

    public static class Args {

    }

    @Data
    public static class LoginRequestArgs extends Args {

        private final String apiKey;
        private final String passphrase;
        private final String timestamp;
        private final String sign;
    }

    @Data
    public static class AccountRequestArgs extends Args {

        private final String channel;   // required
        private final String ccy;    // optional - 	Currency
    }

    @Data
    public static class PositionRequestArgs extends Args {

        private final String channel;   // required
        private final String instType;  // required
        //        private final String uly;       // optional
        private final String instId;    // optional
    }


    @Data
    public static class SubscriptionTopic extends Args {

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
