package com.bitplay.okex.v3.dto.futures;

/**
 * Http Result
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 17/03/2018 11:36
 */
public class HttpResult {

    // {"error_message":"Order price is not within limit","result":"FALSE","error_code":"35014","order_id":"-1"}
    private int code;
    private String message;
    private int error_code;
    private String error_message;
    private String result;
    private String order_id;

    public int getError_code() {
        return error_code;
    }

    public void setError_code(int error_code) {
        this.error_code = error_code;
    }

    public String getError_message() {
        return error_message;
    }

    public void setError_message(String error_message) {
        this.error_message = error_message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }
}
