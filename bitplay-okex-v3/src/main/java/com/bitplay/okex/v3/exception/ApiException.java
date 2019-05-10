package com.bitplay.okex.v3.exception;

/**
 * API Exception
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/8 19:59
 */
public class ApiException extends RuntimeException {

    private int code;

    public ApiException(String message) {
        super(message);
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }


    public ApiException(Throwable cause) {
        super(cause);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        if (this.code != 0) {
            return this.code + " : " + super.getMessage();
        }
        return super.getMessage();
    }
}
