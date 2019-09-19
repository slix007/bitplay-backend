package com.bitplay.api.controller;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.ob.OrderBookJson;
import com.bitplay.api.domain.ob.OrderJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TickerJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.api.service.BitplayUIServicePoloniex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
@RestController
@RequestMapping("/market/poloniex")
public class PoloniexEndpoint {

    @Autowired
    private BitplayUIServicePoloniex poloniex;

    @RequestMapping(value = "/order-book", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson poloniexOrderBook() {
        return this.poloniex.getOrderBook();
    }

    @RequestMapping(value = "/order-book-clean", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderBookJson cleanOrderBook() {
        return new OrderBookJson(); //this.poloniex.cleanOrderBook();
    }

    /* @GET
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
    } */

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

    @RequestMapping(value = "/ticker", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public TickerJson getTicker() {
        return this.poloniex.getTicker();
    }

    @RequestMapping(value = "/place-market-order",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TradeResponseJson placeMarketOrder(@RequestBody TradeRequestJson tradeRequestJson) {
        return this.poloniex.doTrade(tradeRequestJson);
    }

    @RequestMapping(value = "/open-orders", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderJson> openOrders() {
        return this.poloniex.getOpenOrders();
    }

    @RequestMapping(value = "/open-orders/move",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson openOrders(@RequestBody OrderJson orderJson) {
        return this.poloniex.moveOpenOrder(orderJson);
    }

}
