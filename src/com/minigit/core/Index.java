package com.minigit.core;

import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Files;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class Index {
    Map<String, IndexEntry> entries;
    Path indexFile;

    public Index(Path gitDir) {
        indexFile = gitDir.resolve("index");
        entries = new HashMap<>();
    }

    public void load() throws IOException {
        entries.clear();

        if (!Files.exists(indexFile)) {
            return;
        }

        List<String> lines = Files.readAllLines(indexFile);
        for (String line : lines) {
            String[] parts = line.split(",");
            Path p = Path.of(parts[0]);
            String hash = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            entries.put(parts[0], new IndexEntry(p, hash, timestamp));
        }
    }

    public void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (IndexEntry e : entries.values()) {
            lines.add(e.path + "," + e.hash + "," + e.timeStamp);
        }

        Files.write(indexFile, lines);
    }




    public static class IndexEntry {
        public final Path path;
        public final String hash;
        public final long timeStamp;

        public IndexEntry(Path path, String hash, long timeStamp) {
            this.path = path;
            this.hash = hash;
            this.timeStamp = timeStamp;
        }
    }


}
