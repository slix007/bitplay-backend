package com.bitplay.external;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationHome;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationResolverByFile {

    private final HostResolver hostResolver;

    private List<String> lines = null;

    public List<String> getLines() {
        if (lines == null) {
            lines = readSlackSettings();
        }
        return lines;
    }

    public List<String> getSettings() {
        List<String> lines = getLines();
        // check the parsing
        try {
            defineWhereToSend(NotifyType.AT_STARTUP);
        } catch (Exception e) {
            lines = new ArrayList<>();
            lines.add("Can not parse slack.settings file!");
            lines.addAll(Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList()));
        }
        return lines;
    }

    Destination defineWhereToSend(NotifyType notifyType) {
        final String shortName = notifyType.getShortName();
        List<String> channels = new ArrayList<>();
        List<String> lines = getLines();

        // read and parse file slack.settings
        for (String line : lines) {
            if (line.startsWith("#")) {
                continue;
            }

            // example line:
            // trader-active cor, adj, plq
            final String l1 = line.trim();
            if (l1.length() > 0) {
                final String[] split = l1.split(" ");
                final String channelName = split[0];
//                if (split.length > 1 && l1.length() > channelName.length() + 1) {
                final String l2 = l1.substring(channelName.length() + 1);
                final String[] shortTypes = l2.split("\\s*,\\s*");
                final boolean hasType = Arrays.asList(shortTypes).contains(shortName);
                if (hasType) {
                    channels.add(channelName);
                }
//                }
            }
        }

        return new Destination(channels, hostResolver.getHostname());
    }

    private List<String> readSlackSettings() {

        List<String> lines = new ArrayList<>();
        try {
            final ApplicationHome applicationHome = new ApplicationHome();

//            File jarPath = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
//            log.info("jarPath=" + jarPath);
//            String propertiesPath = jarPath.getParentFile().getAbsolutePath();
//            log.info("propertiesPath=" + propertiesPath);
            String propertiesPath = applicationHome.getDir().getAbsolutePath();
            final String filePath = propertiesPath + "/slack.settings";
            log.info(filePath);

            lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            URL u = getClass().getResource("/slack.settings");
            Path p;
            try {
                p = Paths.get(u.toURI());
                lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            } catch (URISyntaxException | IOException e2) {
                e2.printStackTrace();
            }
        }

        return lines;
    }

}
