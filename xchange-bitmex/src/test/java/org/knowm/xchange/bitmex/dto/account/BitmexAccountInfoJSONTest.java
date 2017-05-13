package org.knowm.xchange.bitmex.dto.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import io.swagger.client.RFC3339DateFormat;
import io.swagger.client.model.Wallet;

import static org.junit.Assert.*;

/**
 * Created by Sergey Shurmin on 5/13/17.
 */
public class BitmexAccountInfoJSONTest {

    @Test
    public void testUnmarshal() throws IOException {

        // Read in the JSON from the example resources
        InputStream is = BitmexAccountInfoJSONTest.class.getResourceAsStream("/account/example-wallet-data.json");

        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new JavaTimeModule());

        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        mapper.setDateFormat(new RFC3339DateFormat());
        mapper.registerModule(new JavaTimeModule());


        Wallet wallet = mapper.readValue(is, Wallet.class);

        // Verify that the example data was unmarshalled correctly
        assertEquals(wallet.getCurrency(), "XBt");
//        assertThat(wallet.getHigh()).isEqualTo(new BigDecimal("138.22"));
//        assertThat(wallet.getLow()).isEqualTo(new BigDecimal("131.79"));
//        assertThat(wallet.getVwap()).isEqualTo(new BigDecimal("135.31"));
//        assertThat(wallet.getVolume()).isEqualTo(new BigDecimal("21982.44926674"));
//        assertThat(wallet.getBid()).isEqualTo(new BigDecimal("134.89"));
//        assertThat(wallet.getAsk()).isEqualTo(new BigDecimal("134.92"));
//        assertThat(wallet.getTimestamp()).isEqualTo(1381787133L);
    }

}