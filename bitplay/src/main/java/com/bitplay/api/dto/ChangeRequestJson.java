package com.bitplay.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeRequestJson {
    private String command;

    public ChangeRequestJson() {
    }

    public ChangeRequestJson(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
