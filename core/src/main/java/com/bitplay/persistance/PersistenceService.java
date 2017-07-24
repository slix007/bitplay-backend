package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.repository.DeltaParamsRepository;
import com.bitplay.persistance.repository.GuiParamsRepository;
import com.bitplay.persistance.repository.LiqParamsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 6/16/17.
 */
@Service
public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);

    @Autowired
    private GuiParamsRepository guiParamsRepository;

    @Autowired
    private LiqParamsRepository liqParamsRepository;

    @Autowired
    private DeltaParamsRepository deltaParamsRepository;

    public void saveGuiParams(GuiParams deltas) {
        deltas.setId(1L);
        guiParamsRepository.save(deltas);
    }

    public GuiParams fetchGuiParams() {
        return guiParamsRepository.findFirstByDocumentId(1L);
    }

    public void saveLiqParams(LiqParams liqParams, String marketName) {
        long id = 1L;
        if (marketName.equals("bitmex")) {
            id = 2L;
        }
        if (marketName.equals("okcoin")) {
            id = 3L;
        }
        liqParams.setId(id);
        liqParams.setMarketName(marketName);
        liqParamsRepository.save(liqParams);
    }

    public LiqParams fetchLiqParams(String marketName) {
        return liqParamsRepository.findFirstByMarketName(marketName);
    }

    public void storeDeltaParams(DeltaParams deltaParams) {
        deltaParams.setId(1L);
        deltaParamsRepository.save(deltaParams);
    }

    public DeltaParams fetchDeltaParams() {
        return deltaParamsRepository.findFirstByDocumentId(1L);
    }
}
