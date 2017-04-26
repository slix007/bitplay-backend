package com.bitplay.controller;

import com.bitplay.domain.AccountInfoJson;
import com.bitplay.domain.OrderBookJson;
import com.bitplay.domain.OrderJson;
import com.bitplay.domain.ResultJson;
import com.bitplay.domain.TickerJson;
import com.bitplay.domain.TradeRequestJson;
import com.bitplay.domain.TradeResponseJson;
import com.bitplay.domain.VisualTrade;
import com.bitplay.service.BitplayUIServicePoloniex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
@Component
@Path("/market/poloniex")
public class PoloniexEndpoint {

    @Autowired
    private BitplayUIServicePoloniex poloniex;

    @GET
    @Path("/order-book")
    @Produces("application/json")
    public OrderBookJson poloniexOrderBook() {
        return this.poloniex.getOrderBook();
    }

    @GET
    @Path("/order-book-clean")
    @Produces("application/json")
    public OrderBookJson cleanOrderBook() {
        return this.poloniex.cleanOrderBook();
    }

    @GET
    @Path("/order-book-fetch")
    @Produces("application/json")
    public OrderBookJson fetchAndCompareOrderBook() {
        final OrderBookJson computed = this.poloniex.fetchOrderBook();
//        compareOrderBook(computed);
        return computed;
    }

    private void compareOrderBook(OrderBookJson computed) {
        final OrderBookJson fetched = this.poloniex.fetchOrderBook();
        final boolean computedAskHasAllReal = computed.getAsk().containsAll(fetched.getAsk());
        final boolean computedBidHasAllReal = computed.getBid().containsAll(fetched.getBid());
        System.out.println("computedAskHasAllReal " + computedAskHasAllReal);
        System.out.println("computedBidHasAllReal " + computedBidHasAllReal);
        final boolean fetchedAskHasAllComputed = fetched.getAsk().containsAll(computed.getAsk());
        final boolean fetchedBidHasAllComputed = fetched.getBid().containsAll(computed.getBid());
        System.out.println("fetchedAskHasAllComputed " + fetchedAskHasAllComputed);
        System.out.println("fetchedBidHasAllComputed " + fetchedBidHasAllComputed);

        computed.getAsk().forEach(oneItem -> {
            findMatchedByPrice(oneItem, fetched.getAsk());
        });
        computed.getBid().forEach(oneItem -> {
            findMatchedByPrice(oneItem, fetched.getBid());
        });
    }

    private void findMatchedByPrice(OrderJson toFindOrderJson, List<OrderJson> ask) {
        final String price = toFindOrderJson.getPrice();
        final BigDecimal toFind = new BigDecimal(price);
        final List<OrderJson> foundMatches = ask.stream()
                .filter(orderJson -> new BigDecimal(orderJson.getPrice()).compareTo(toFind) == 0)
                .collect(Collectors.toList());
        if (foundMatches.size() == 0) {
            System.out.println(String.format("No matches for %s, %s, %s, %s",
                    price, toFindOrderJson.getOrderType(), toFindOrderJson.getTimestamp(), toFindOrderJson.getAmount()));
        } else if (foundMatches.size() > 1) {
            System.out.println("More than one mathches found for " + price);
        } else {
            // compare amount
            final OrderJson matched = foundMatches.get(0);
            if (new BigDecimal(matched.getAmount()).compareTo(new BigDecimal(toFindOrderJson.getAmount())) != 0) {
                System.out.println(String.format("Amounts don't match. %s, %s!=%s. ToFind=%s, Real=%s",
                        toFindOrderJson.getOrderType(), toFindOrderJson.getAmount(), matched.getAmount(),
                        toFindOrderJson.toString(), matched.toString()));
            }
        }
    }

    @GET
    @Path("/ticker")
    @Produces("application/json")
    public TickerJson getTicker() {
        return this.poloniex.getTicker();
    }

    @GET
    @Path("/account")
    @Produces("application/json")
    public AccountInfoJson getAccountInfo() {
        return this.poloniex.getAccountInfo();
    }

    @POST
    @Path("/place-market-order")
    @Consumes("application/json")
    @Produces("application/json")
    public TradeResponseJson placeMarketOrder(TradeRequestJson tradeRequestJson) {
        return this.poloniex.doTrade(tradeRequestJson);
    }

    @GET
    @Path("/trade-history")
    @Produces("application/json")
    public List<VisualTrade> tradeHistory() {
        return this.poloniex.fetchTrades();
    }

    @GET
    @Path("/open-orders")
    @Produces("application/json")
    public List<OrderJson> openOrders() {
        return this.poloniex.getOpenOrders();
    }

    @POST
    @Path("/open-orders/move")
    @Produces("application/json")
    public ResultJson openOrders(OrderJson orderJson) {
        return this.poloniex.moveOpenOrder(orderJson);
    }

}
