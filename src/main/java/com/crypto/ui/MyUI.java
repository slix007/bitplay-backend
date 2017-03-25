package com.crypto.ui;

import com.crypto.polonex.PoloniexService;
import com.crypto.service.BitplayUIServicePoloniex;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Binder;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Button;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ListSelect;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/24/17.
 */
@Theme("valo")
@SpringUI
public class MyUI extends UI {

    @Autowired
    @Qualifier("Poloniex")
    BitplayUIServicePoloniex bitplayUIServicePoloniex;

    @Autowired
    PoloniexService poloniexService;

//    private Grid<VisualTrade> grid = new Grid<>(VisualTrade.class);

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();
        layout.addComponent(new Label("Hello! I'm the root Layout!"));
        addTradingGrid(layout);

        addAccountInfo(layout);

        setContent(layout);
    }

    private void addAccountInfo(VerticalLayout layout) {
        final AccountInfo accountInfo = poloniexService.fetchAccountInfo();
//        final Balance balance = accountInfo.getWallet().getBalance(Currency.BTC);
        ListDataProvider<Balance> dataProvider =
                DataProvider.ofItems(
                        accountInfo.getWallet().getBalance(Currency.BTC),
                        accountInfo.getWallet().getBalance(Currency.USD)
                );
//
        ListSelect<Balance> comboBox = new ListSelect<>();
        comboBox.setDataProvider(dataProvider);
        comboBox.setWidth(90, Unit.PERCENTAGE);
        layout.addComponent(comboBox);
    }


    private void addTradingGrid(VerticalLayout layout) {
        final HorizontalLayout gridPanel = new HorizontalLayout();
        List<VisualTrade> trades = bitplayUIServicePoloniex.fetchTrades();
        List<VisualTrade> askTrades = trades.stream()
                .filter(trade -> Order.OrderType.valueOf(trade.getOrderType()) == Order.OrderType.ASK)
                .collect(Collectors.toList());
        List<VisualTrade> bidTrades = trades.stream()
                .filter(trade -> Order.OrderType.valueOf(trade.getOrderType()) == Order.OrderType.BID)
                .collect(Collectors.toList());

        Button resetButton = new Button("Update",
                event -> {
                    trades.clear();
                    trades.addAll(bitplayUIServicePoloniex.fetchTrades());
                    askTrades.clear();
                    askTrades.addAll(trades.stream()
                            .filter(trade -> Order.OrderType.valueOf(trade.getOrderType()) == Order.OrderType.ASK)
                            .collect(Collectors.toList()));
                    askTrades.clear();
                    bidTrades.addAll(trades.stream()
                            .filter(trade -> Order.OrderType.valueOf(trade.getOrderType()) == Order.OrderType.BID)
                            .collect(Collectors.toList()));
                });
        layout.addComponent(resetButton);


        Grid<VisualTrade> gridAsk = createTradingGrid(askTrades);
        Grid<VisualTrade> gridBid = createTradingGrid(bidTrades);

        gridPanel.addComponentsAndExpand(gridBid);
        gridPanel.addComponentsAndExpand(gridAsk);
        layout.addComponentsAndExpand(gridPanel);
    }


    private Grid<VisualTrade> createTradingGrid(List<VisualTrade> trades) {

        ListDataProvider<VisualTrade> listDataProvider = new ListDataProvider<>(trades);


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