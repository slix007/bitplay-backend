package com.bitplay.market;

public interface LogService {

    void warn(String s, String... args);

    void info(String s, String... args);

    void error(String s, String... args);
}
