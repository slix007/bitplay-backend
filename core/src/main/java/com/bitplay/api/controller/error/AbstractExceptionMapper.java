package com.bitplay.api.controller.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import javax.ws.rs.core.Response;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
public abstract class AbstractExceptionMapper {
    private static final Logger logger = LoggerFactory.getLogger(AbstractExceptionMapper.class);


    protected Response errorResponse(int status, ResponseEntity responseEntity) {
        return customizeResponse(status, responseEntity);
    }

    protected Response errorResponse(int status, ResponseEntity responseEntity, Throwable t) {
//        logger.error("errorResponse", t); // logging stack trace.

        return customizeResponse(status, responseEntity);
    }

    private Response customizeResponse(int status, ResponseEntity responseEntity) {
        return Response.status(status).entity(responseEntity).build();
    }
}
