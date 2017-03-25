package com.crypto.ui;

import com.crypto.service.BitplayUIServicePoloniex;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Binder;
import com.vaadin.data.provider.BackEndDataProvider;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Layout;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import org.knowm.xchange.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Created by Sergey Shurmin on 3/24/17.
 */
@Theme("valo")
@SpringUI
public class MyUI extends UI {

    @Autowired
    @Qualifier("Poloniex")
    BitplayUIServicePoloniex bitplayUIServicePoloniex;

//    private Grid<VisualTrade> grid = new Grid<>(VisualTrade.class);

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();
        layout.addComponent(new Label("Hello! I'm the root Layout!"));
        addTradingGrid(layout);

        setContent(layout);
    }



    private void addTradingGrid(VerticalLayout layout) {
//        final PoloniexService poloniexExample = new PoloniexService();
//        Trades trades = poloniexExample.fetchTrades();

//        Button resetButton = new Button("Reset",
//                event -> poloniexExample.fetchTrades());
//        layout.addComponent(resetButton);


        final HorizontalLayout gridPanel = new HorizontalLayout();
//        final Panel gridPanel = new Panel(layout);
        Grid<VisualTrade> gridAsk = createTradingGrid(Order.OrderType.ASK);
        Grid<VisualTrade> gridBid = createTradingGrid(Order.OrderType.BID);

//        final VerticalLayout gridLayoutAsk = new VerticalLayout();
//        gridLayoutAsk.addComponents(new Label("ASK"));
//        gridLayoutAsk.addComponentsAndExpand(gridAsk);
//
//        final VerticalLayout gridLayoutBid = new VerticalLayout();
//        gridLayoutBid.addComponents(new Label("BID"));
//        gridLayoutBid.addComponentsAndExpand(gridBid);

//        gridPanel.addComponentsAndExpand(gridLayoutAsk);
//        gridPanel.addComponentsAndExpand(gridLayoutBid);

        gridPanel.addComponentsAndExpand(gridBid);
        gridPanel.addComponentsAndExpand(gridAsk);
        layout.addComponentsAndExpand(gridPanel);


//        addUpdateButton(layout, bidTrades.get(0));
//
//        ListDataProvider<VisualTrade> dataProvider =
//                DataProvider.ofCollection(askTrades);
//
////        dataProvider.setSortOrder(Person::getName,
////                SortDirection.ASCENDING);
//
//        ComboBox<VisualTrade> comboBox = new ComboBox<>();
//// The combo box shows the persons sorted by name
//        comboBox.setDataProvider(dataProvider);
//        comboBox.setWidth(90, Unit.PERCENTAGE);
//        layout.addComponent(comboBox);

    }


    private Grid<VisualTrade> createTradingGrid(Order.OrderType orderType) {
//        ListDataProvider<VisualTrade> dataProvider =
//                DataProvider.ofCollection(vTrades);

        BackEndDataProvider<VisualTrade, Void> dataProvider = DataProvider.fromCallbacks(
                // First callback fetches items based on a query
                query -> {
                    List<VisualTrade> trades = bitplayUIServicePoloniex.fetchTrades();

//                    final OrderBook orderBook = bitplayUIServicePoloniex.fetchOrderBook();
//                    final List<VisualTrade> trades;
//                    if (orderType == Order.OrderType.ASK) {
//                        trades = bitplayUIServicePoloniex.getAsks();
//                    } else {
//                        trades = bitplayUIServicePoloniex.getBids();
//                    }

                    return trades.stream()
                            .filter(trade -> Order.OrderType.valueOf(trade.getOrderType()) == orderType);

                },
                // Second callback fetches the number of items for a query
                query -> bitplayUIServicePoloniex.getOrderBookDepth()
        );

        // Create a grid bound to the list
        Grid<VisualTrade> grid = new Grid<>();
        grid.setDataProvider(dataProvider);

//        grid.setItems(vTrades);
        grid.addColumn(VisualTrade::getTimestamp).setCaption("Time");
        grid.addColumn(VisualTrade::getCurrency).setCaption("Currency");
        grid.addColumn(VisualTrade::getPrice).setCaption("Price");
        grid.addColumn(VisualTrade::getAmount).setCaption("Amount");
        grid.addColumn(VisualTrade::getOrderType).setCaption("Order");


        return grid;
    }


    private void addUpdateButton(VerticalLayout layout, VisualTrade visualTrade) {
        Binder<VisualTrade> binder = new Binder<>();

        TextField nameField = new TextField();

        binder.bind(nameField, VisualTrade::getPrice, VisualTrade::setPrice);

        binder.readBean(visualTrade);

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
//        Button resetButton = new Button("Reset",
//                event -> binder.readBean(visualTrade));

        layout.addComponent(nameField);
//        layout.addComponent(updateButton);
//        layout.addComponent(resetButton);

    }


}