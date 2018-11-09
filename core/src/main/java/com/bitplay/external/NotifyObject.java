package com.bitplay.external;

import lombok.Data;
import lombok.NonNull;

@Data
public class NotifyObject {

    @NonNull
    private String text;
    @NonNull
    private String channel;

}
