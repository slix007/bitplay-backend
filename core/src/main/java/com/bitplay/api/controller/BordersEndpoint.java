package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Shurmin on 10/6/17.
 */
@RestController
@RequestMapping("/borders")
public class BordersEndpoint {

    @Autowired
    PersistenceService persistenceService;

    @RequestMapping(value = "/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BorderParams> getBorders() {
        return persistenceService.fetchBorders();
    }

    @RequestMapping(value = "/create-default", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BorderParams> setDefaultBorders() {
        final List<BorderParams> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, -21, 500, 500));
        borderBtmClose.add(new BorderItem(2, -15, 300, 350));
        borderBtmClose.add(new BorderItem(3, -10, 200, 250));
        borderBtmClose.add(new BorderItem(4, -5, 100, 100));
        borders.add(new BorderParams("btm_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, 20, 100, 100));
        borderBtmOpen.add(new BorderItem(2, 30, 250, 250));
        borderBtmOpen.add(new BorderItem(3, 35, 350, 350));
        borders.add(new BorderParams("btm_br_open", borderBtmOpen));

        persistenceService.saveBorders(borders);

        return persistenceService.fetchBorders();
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson saveBorders(@RequestBody List<BorderParams> borderParamsList) {
        persistenceService.saveBorders(borderParamsList);

        return new ResultJson("OK", "");
    }
}
