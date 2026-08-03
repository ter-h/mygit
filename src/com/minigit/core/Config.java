package com.minigit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    Map<String, String> values;
    Path configFile;

    public Config(Path gitDir) {
        configFile = gitDir.resolve("config");

        values = new HashMap<>();
    }

    public void load() throws IOException {
        values.clear();

        if (!Files.exists(configFile)) {
            return;
        }

        List<String> lines = Files.readAllLines(configFile);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            values.put(key, value);
        }
    }

    public void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue());
        }

        Files.write(configFile, lines);
    }

    public void set(String key, String value) {
        values.put(key, value);
    }

    public String get(String key) {
        return values.get(key);
    }
}
