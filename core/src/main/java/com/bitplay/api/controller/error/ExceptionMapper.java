package com.bitplay.api.controller.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;


/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Provider
public class ExceptionMapper extends AbstractExceptionMapper implements
    javax.ws.rs.ext.ExceptionMapper<Exception> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractExceptionMapper.class);

    @Override
        public Response toResponse(Exception e) {
            // ResponseEntity class's Member Integer code, String message, Object data. For response format.


            ResponseEntity<Exception> re = new ResponseEntity<>(e, HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("errorResponse", e); // logging stack trace.

            return this.errorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), re, e);
        }
}
