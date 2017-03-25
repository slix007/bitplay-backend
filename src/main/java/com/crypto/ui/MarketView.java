package com.crypto.ui;

import com.vaadin.ui.Button;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public class MarketView extends VerticalLayout {

//    TextField entry = new TextField("Enter this");
    Label display = new Label("See this");
    Button click = new Button("Refresh");

    public MarketView() {
//        addComponent(entry);
        addComponent(display);
        addComponent(click);

        setSizeFull();
        addStyleName("myview");
    }
}
