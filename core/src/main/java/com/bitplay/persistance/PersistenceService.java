package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.repository.DeltasRepository;

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
    private DeltasRepository repository;

    public void saveDeltas(GuiParams deltas) {
        deltas.setId(1L);
        repository.save(deltas);
    }

    public GuiParams fetchDeltas() {
        return repository.findFirstByDocumentId(1L);
    }

}
