package com.bitplay.external;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class SlackNotificationsTest {

    @Mock
    ch.qos.logback.core.Appender appender;

    @Before
    public void setup() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        when(appender.getName()).thenReturn("MOCK");
        when(appender.isStarted()).thenReturn(true);
        logger.addAppender(appender);
    }

    @Test
    public void sendNotify() {

        SlackNotifications slackNotifications = new SlackNotifications();
        String theChannel = "localchannel";
        slackNotifications.sendSync("mytest", "test", theChannel);
    }
}