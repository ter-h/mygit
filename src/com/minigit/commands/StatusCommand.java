package com.minigit.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.minigit.core.Index.IndexEntry;
import com.minigit.core.ObjectStore;
import com.minigit.core.ObjectStore.RawObject;
import com.minigit.core.ObjectStore.TreeEntry;
import com.minigit.core.Repository;

public class StatusCommand implements Command {
    @Override
    public void execute(String[] args) throws IOException {
        Path base = Paths.get(System.getProperty("user.dir"));
        Repository repo = new Repository(base);
        repo.getIndex().load();

        Map<String, IndexEntry> staged = repo.getIndex().getEntries();
        Map<String, String> committed = new HashMap<>();

        String commitHash = repo.getCurrentCommitHash();

        if (commitHash != null) {
            RawObject commitObj = repo.getObjectStore().readObject(commitHash);
            String commitText = new String(commitObj.content, StandardCharsets.UTF_8);

            int separator = commitText.indexOf("\n\n");
            String header = commitText.substring(0, separator);

            String treeHash = null;
            for (String line : header.split("\n")) {
                int space = line.indexOf(" ");
                String key = line.substring(0, space);
                String value = line.substring(space + 1);
                if (key.equals("tree")) {
                    treeHash = value;
                }
            }

            for (TreeEntry entry : repo.getObjectStore().readTree(treeHash)) {
                committed.put(entry.name, entry.hash);
            }
        }

        try (Stream<Path> paths = Files.walk(repo.getWorkingDir())) {

            paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .forEach(path -> {
                    try {
                        Path relPath = repo.getWorkingDir().relativize(path);
                        IndexEntry entry = staged.get(relPath.toString());

                        if (entry != null) {
                            byte[] currentBytes = Files.readAllBytes(path);
                            String currentHash = ObjectStore.hashObject("blob", currentBytes);
                            if (currentHash.equals(entry.hash)) {
                                System.out.println("staged, unchanged: " + path);
                            } else {
                                System.out.println("modified (staged): " + path);
                            }
                            return;
                        }

                        String committedHash = committed.get(path.getFileName().toString());

                        if (committedHash == null) {
                            System.out.println("untracked: " + path);
                            return;
                        }

                        byte[] currentBytes = Files.readAllBytes(path);
                        String currentHash = ObjectStore.hashObject("blob", currentBytes);

                        if (currentHash.equals(committedHash)) {
                            System.out.println("unchanged: " + path);
                        } else {
                            System.out.println("modified (not staged): " + path);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        }

    }
}
