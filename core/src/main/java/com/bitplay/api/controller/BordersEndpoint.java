package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV1;
import com.bitplay.persistance.domain.BordersV2;

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

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public BorderParams getBorders() {
        return persistenceService.fetchBorders();
    }

    @RequestMapping(value = "/tables", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BorderTable> getBorderTables() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        return (borderParams != null && borderParams.getBordersV2() != null)
                ? borderParams.getBordersV2().getBorderTableList() : new ArrayList<>();
    }

    @RequestMapping(value = "/create-default", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public BorderParams setDefaultBorders() {
        final BorderParams borderParams = createDefaultBorders();

        persistenceService.saveBorderParams(borderParams);

        return persistenceService.fetchBorders();
    }

    private BorderParams createDefaultBorders() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(-21), 500, 500));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(-15), 300, 350));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(-10), 200, 250));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(-5), 100, 100));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 100, 100));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 250, 250));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(35), 350, 350));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(-21), 500, 500));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(-15), 300, 350));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(-10), 200, 250));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-5), 100, 100));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 100, 100));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 250, 250));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(35), 350, 350));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
    }

    @RequestMapping(value = "/tables", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson saveBorders(@RequestBody List<BorderTable> borderTableList) {
        if (borderTableList == null || borderTableList.size() != 1) {
            return new ResultJson("Wrong request", "Request body should have one borderTable. " + borderTableList);
        }
        final BorderTable updatedTable = borderTableList.get(0);

        final BorderParams borderParams = persistenceService.fetchBorders();

        final List<BorderTable> currentList = borderParams.getBordersV2().getBorderTableList();
        currentList.replaceAll(borderTable -> {

            if (updatedTable.getBorderName().equals(borderTable.getBorderName())) return updatedTable;

            return borderTable;
        });

        persistenceService.saveBorderParams(borderParams);

        return new ResultJson("OK", "");
    }

    @RequestMapping(value = "/settings", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson updateBordersSettings(@RequestBody BordersSettings settings) {
        final BorderParams bP = persistenceService.fetchBorders();

        String respDetails = "";
        try {
            if (settings.version != null) {
                bP.setActiveVersion(BorderParams.Ver.valueOf(settings.version));
                respDetails = "ver: " + settings.version;
            }
            if (settings.posMode != null) {
                bP.setPosMode(BorderParams.PosMode.valueOf(settings.posMode));
                respDetails += " posMode: " + settings.posMode;
            }
        } catch (Exception e) {
            return new ResultJson("Wrong version", e.getMessage());
        }

        persistenceService.saveBorderParams(bP);

        return new ResultJson("OK", respDetails);
    }

    private static class BordersSettings {
        public String version;
        public String posMode;
    }
}
