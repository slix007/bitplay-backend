package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.repository.DeltasRepository;
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
    private DeltasRepository deltasRepository;

    @Autowired
    private LiqParamsRepository liqParamsRepository;

    public void saveDeltas(GuiParams deltas) {
        deltas.setId(1L);
        deltasRepository.save(deltas);
    }

    public GuiParams fetchDeltas() {
        return deltasRepository.findFirstByDocumentId(1L);
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
}
