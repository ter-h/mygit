package com.minigit.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

        Map<String, String> working = new HashMap<>();
        try (Stream<Path> paths = Files.walk(repo.getWorkingDir())) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains(".git"))::iterator) {
                Path relPath = repo.getWorkingDir().relativize(path);
                byte[] bytes = Files.readAllBytes(path);
                working.put(relPath.toString(), ObjectStore.hashObject("blob", bytes));
            }
        }

        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(staged.keySet());
        allPaths.addAll(committed.keySet());
        allPaths.addAll(working.keySet());

        StringBuilder toBeCommitted = new StringBuilder();
        StringBuilder notStaged = new StringBuilder();
        StringBuilder untracked = new StringBuilder();

        for (String path : allPaths) {
            boolean inIndex = staged.containsKey(path);
            boolean inCommit = committed.containsKey(path);
            boolean inWorking = working.containsKey(path);

            String indexHash = inIndex ? staged.get(path).hash : null;
            String commitFileHash = committed.get(path);
            String workingHash = working.get(path);

            // Section 1: index vs HEAD ("changes to be committed")
            if (inIndex && !inCommit) {
                toBeCommitted.append("\tnew file:   ").append(path).append("\n");
            } else if (inIndex && inCommit && !indexHash.equals(commitFileHash)) {
                toBeCommitted.append("\tmodified:   ").append(path).append("\n");
            } else if (!inIndex && inCommit) {
                toBeCommitted.append("\tdeleted:    ").append(path).append("\n");
            }

            // Section 2: working dir vs index ("changes not staged for commit")
            if (inIndex && inWorking && !workingHash.equals(indexHash)) {
                notStaged.append("\tmodified:   ").append(path).append("\n");
            } else if (inIndex && !inWorking) {
                notStaged.append("\tdeleted:    ").append(path).append("\n");
            }

            // Section 3: untracked (in neither index nor commit, present on disk)
            if (!inIndex && !inCommit && inWorking) {
                untracked.append("\t").append(path).append("\n");
            }
        }

        if (toBeCommitted.length() > 0) {
            System.out.println("Changes to be committed:");
            System.out.print(toBeCommitted);
            System.out.println();
        }

        if (notStaged.length() > 0) {
            System.out.println("Changes not staged for commit:");
            System.out.print(notStaged);
            System.out.println();
        }

        if (untracked.length() > 0) {
            System.out.println("Untracked files:");
            System.out.print(untracked);
            System.out.println();
        }

        if (toBeCommitted.length() == 0 && notStaged.length() == 0 && untracked.length() == 0) {
            System.out.println("nothing to commit, working tree clean");
        }
    }
}
