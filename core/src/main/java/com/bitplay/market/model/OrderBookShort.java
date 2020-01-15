package com.bitplay.market.model;

import info.bitrich.xchangestream.bitmex.dto.BitmexQuote;
import info.bitrich.xchangestream.bitmex.dto.BitmexQuoteLine;
import lombok.Data;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

@Data
public class OrderBookShort {
    private volatile OrderBook ob = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    private volatile Date creatQuoteTime;
    private volatile Instant saveObTime;

    public OrderBookShort() {
    }

    // several threads: subscribe to OB, get OB via REST
    public void setOb(OrderBook ob) {
        setOb(ob, null);
    }

    public void setOb(OrderBook ob, Date creatQuoteTime) {
        setOb(ob, creatQuoteTime, Instant.now());
    }

    public void setOb(OrderBook ob, Date creatQuoteDate, Instant saveObTime) {
        this.creatQuoteTime = creatQuoteDate;
        this.ob = ob; // get ob time inside.
        this.saveObTime = saveObTime;
    }

    public Instant getCreateQuoteInstant() {
        return creatQuoteTime != null ? creatQuoteTime.toInstant() : null;
    }

    // get quote single thread
    public void updateMarketTimestamp(BitmexQuoteLine bitmexQuoteLine) {
        OrderBook orderBook = this.ob;
        if (orderBook.getBids().size() == 0 || orderBook.getAsks().size() == 0) {
            return;
        }

        final Date obT = orderBook.getTimeStamp();
        final LimitOrder bestAsk = orderBook.getAsks().get(0);
        final LimitOrder bestBid = orderBook.getBids().get(0);
        int matchesCount = 0;
        Date lastDt = null;
        Long marketToUsMs = null;
        for (BitmexQuote q : bitmexQuoteLine.getData()) {
            if (q.getAskPrice().compareTo(bestAsk.getLimitPrice()) == 0
                    && q.getBidPrice().compareTo(bestBid.getLimitPrice()) == 0
                    && q.getAskSize().compareTo(bestAsk.getTradableAmount()) == 0
                    && q.getBidSize().compareTo(bestBid.getTradableAmount()) == 0
            ) {
                matchesCount++;
                final Date qT = q.getTimestamp();
//                final SimpleDateFormat sdf = new SimpleDateFormat("mm.ss.SSS");
//                final long marketToUsMs = obT.getTime() - qT.getTime();

//                System.out.println("marketToUsMs=" + marketToUsMs + ". " + bitmexQuoteLine.getData().size());
//                logger.info("marketToUsMs=" + marketToUsMs + ". data.size()=" + bitmexQuoteLine.getData().size());
//                        + ", obT=" + sdf.format(obT) + ", qT" + sdf.format(qT));
//                metricsDictionary.putBitmex_plBefore_ob_saveTime_incremental_market(marketToUsMs);
//                this.quoteDate = qT;
//                break;

                if (lastDt == null || qT.after(lastDt)) {
                    lastDt = qT;
                    marketToUsMs = Duration.between(qT.toInstant(), obT.toInstant()).toMillis();
                }
            }
        }

        this.creatQuoteTime = lastDt;
//        System.out.println("marketToUsMs=" + marketToUsMs + ". quote.size=" + bitmexQuoteLine.getData().size()
//                + ", quote.matches=" + matchesCount);
    }
}
