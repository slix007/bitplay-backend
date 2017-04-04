package com.crypto.ui;

import com.crypto.model.VisualTrade;
import com.crypto.polonex.PoloniexService;
import com.crypto.service.BitplayUIServicePoloniex;
import com.vaadin.data.Binder;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Button;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;
import com.vaadin.ui.ListSelect;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Shurmin on 3/24/17.
 */
//@Theme("valo")
//@SpringUI
public class MyUI extends UI {

    @Autowired
    @Qualifier("Poloniex")
    BitplayUIServicePoloniex bitplayUIServicePoloniex;

    @Autowired
    PoloniexService poloniexService;

//    private Grid<VisualTrade> grid = new Grid<>(VisualTrade.class);
    List<VisualTrade> askTrades;
    List<VisualTrade> bidTrades;
    ListDataProvider<VisualTrade> listDataProvider;

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();
        layout.addComponent(new Label("Hello! I'm the root Layout!"));

        addTickerSymbolCurrentStatus(layout);

        addOrderBook(layout);

        addTradingGrid(layout);

        addAccountInfo(layout);

        setContent(layout);
    }

    private void addOrderBook(VerticalLayout layout) {
        final OrderBook orderBook = poloniexService.fetchOrderBook();

        ListDataProvider<LimitOrder> dataProviderAsk = new ListDataProvider<>(orderBook.getAsks());
        Grid<LimitOrder> gridAsk = new Grid<>();
        gridAsk.setDataProvider(dataProviderAsk);
        gridAsk.addColumn(limitOrder -> limitOrder.getCurrencyPair().toString()).setCaption("Currency");
        gridAsk.addColumn(LimitOrder::getLimitPrice).setCaption("LimitPrice");
        gridAsk.addColumn(Order::getTradableAmount).setCaption("TradableAmount");
        gridAsk.addColumn(Order::getType).setCaption("Type");

        ListDataProvider<LimitOrder> dataProviderBid = new ListDataProvider<>(orderBook.getBids());
        Grid<LimitOrder> gridBid = new Grid<>();
        gridBid.setDataProvider(dataProviderBid);
        gridBid.addColumn(limitOrder -> limitOrder.getCurrencyPair().toString()).setCaption("Currency");
        gridBid.addColumn(LimitOrder::getLimitPrice).setCaption("LimitPrice");
        gridBid.addColumn(Order::getTradableAmount).setCaption("TradableAmount");
        gridBid.addColumn(Order::getType).setCaption("Type");

        Button resetButton = new Button("Update",
                event -> {
                    final OrderBook orderBookUpdate = poloniexService.fetchOrderBook();
                    dataProviderAsk.getItems().clear();
                    dataProviderAsk.getItems().addAll(orderBookUpdate.getAsks());
                    dataProviderAsk.refreshAll();
                    dataProviderBid.getItems().clear();
                    dataProviderBid.getItems().addAll(orderBookUpdate.getBids());
                    dataProviderBid.refreshAll();
                });
        layout.addComponent(resetButton);

        final HorizontalLayout orderBookLayout = new HorizontalLayout();
        orderBookLayout.addComponentsAndExpand(gridAsk);
        orderBookLayout.addComponentsAndExpand(gridBid);

        layout.addComponent(orderBookLayout);
    }

    private void addTickerSymbolCurrentStatus(Layout layout) {
        Label label = new Label("");
        label.setValue("value");
        poloniexService.initStreaming().subscribe((Ticker ticker) -> {
            label.setValue(ticker.toString());
            label.setData(ticker.toString());
            label.markAsDirty();
            System.out.println("Incoming ticker: " + ticker);
        }, throwable -> {
            label.setValue("Error in subscribing tickers. " + throwable.getMessage());
        });
        layout.addComponent(label);
    }

    private void addAccountInfo(VerticalLayout layout) {
        final AccountInfo accountInfo = poloniexService.fetchAccountInfo();
//        final Balance balance = accountInfo.getWallet().getBalance(Currency.BTC);
        List<String> balancesList = new ArrayList<>();
        accountInfo.getWallet().getBalances().forEach((currency, balance) -> {
            if (balance.getTotal().intValue() != 0) {
                balancesList.add(String.format("%s: %s", currency.getCurrencyCode(), balance.toString()));

            }
        });

        ListDataProvider<String> dataProvider = //DataProvider.fromStream(balancesList.stream());
                DataProvider.ofItems(
                        accountInfo.getWallet().getBalance(Currency.BTC).toString(),
                        accountInfo.getWallet().getBalance(new Currency("USDT")).toString()
                );
//
        ListSelect<String> listSelect = new ListSelect<>();
        listSelect.setDataProvider(dataProvider);
        listSelect.setWidth(90, Unit.PERCENTAGE);
        layout.addComponent(listSelect);
    }


    private void addTradingGrid(VerticalLayout layout) {
        final HorizontalLayout gridPanel = new HorizontalLayout();
        List<VisualTrade> trades = bitplayUIServicePoloniex.fetchTrades();

        Button resetButton = new Button("Update",
                event -> {
                    trades.clear();
                    final List<VisualTrade> updates = bitplayUIServicePoloniex.fetchTrades();
                    trades.addAll(updates);
                    listDataProvider.refreshAll();
                });
        layout.addComponent(resetButton);

        Grid<VisualTrade> tradesGrid = createTradingGrid(trades);
        gridPanel.addComponentsAndExpand(tradesGrid);
        layout.addComponentsAndExpand(gridPanel);
    }


    private Grid<VisualTrade> createTradingGrid(List<VisualTrade> trades) {
        listDataProvider = new ListDataProvider<>(trades);
        // Create a grid bound to the list
        Grid<VisualTrade> grid = new Grid<>();
        grid.setDataProvider(listDataProvider);

//        grid.setItems(vTrades);
        grid.addColumn(VisualTrade::getTimestamp).setCaption("Time");
        grid.addColumn(VisualTrade::getCurrency).setCaption("Currency");
        grid.addColumn(VisualTrade::getPrice).setCaption("Price");
        grid.addColumn(VisualTrade::getAmount).setCaption("Amount");
        grid.addColumn(VisualTrade::getOrderType).setCaption("Order");


        return grid;
    }


    private void addUpdateButton(VerticalLayout layout, List<VisualTrade> trades) {
        Binder<VisualTrade> binder = new Binder<>();

        TextField nameField = new TextField();

        binder.bind(nameField, VisualTrade::getPrice, VisualTrade::setPrice);

        binder.readBean(trades.get(0));

//        Button updateButton = new Button("Update",
//                event -> {
//                    try {
//                        binder.writeBean(visualTrade);
//                        // A real application would also save the updated person
//                        // using the application's backend
//                    } catch (ValidationException e) {
//                        Notification.show("Can not update");
//                    }
//                });
//
//        // Updates the fields again with the previously saved values
        Button resetButton = new Button("Reset",
                event -> binder.readBean(trades.get(0)));

        layout.addComponent(nameField);
//        layout.addComponent(updateButton);
        layout.addComponent(resetButton);
    }

}