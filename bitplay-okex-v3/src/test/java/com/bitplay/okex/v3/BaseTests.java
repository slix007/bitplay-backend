package com.bitplay.okex.v3;

import com.bitplay.okex.v3.helper.OkexObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;

/**
 * Junit base Tests
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/12 14:48
 */
public class BaseTests {

    public ApiConfiguration config;

    public void toResultString(Logger log, String flag, Object object) throws JsonProcessingException {
        StringBuilder su = new StringBuilder();
        su.append("\n").append("<*> ").append(flag).append(": ").append(
                OkexObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(object));
        log.info(su.toString());
    }
}
