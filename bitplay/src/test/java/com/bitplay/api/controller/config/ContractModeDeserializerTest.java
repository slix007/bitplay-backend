package com.bitplay.api.controller.config;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@JsonTest
@RunWith(SpringRunner.class)
public class ContractModeDeserializerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testDeserialize() throws IOException {
        String json = "{ \"left\" : \"ETHUSD\" }";
        ContractMode contractMode = objectMapper.readValue(json, ContractMode.class);
        assertEquals(new ContractMode(BitmexContractType.ETHUSD_Perpetual, null), contractMode);
    }

    @Test
    public void testDeserialize2() throws IOException {
        String json = "{ \"left\" : \"ETHM20\" }";
        ContractMode contractMode = objectMapper.readValue(json, ContractMode.class);
        assertEquals(new ContractMode(BitmexContractType.ETHUSD_Perpetual, null), contractMode);
    }
}
