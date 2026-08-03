package com.minigit.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.minigit.core.Index.IndexEntry;
import com.minigit.core.ObjectStore.RawObject;
import com.minigit.core.ObjectStore.TreeEntry;
import com.minigit.core.Repository;

public class CommitCommand implements Command {
    @Override
    public void execute(String[] args) throws IOException {
        String flag = args[0];
        String message = args[1];
        Path base = Paths.get(System.getProperty("user.dir"));
        Repository repo = new Repository(base);
        repo.getIndex().load();

        List<TreeEntry> entries = new ArrayList<>();
        Map<String, String> staged = new HashMap<>();

        for (IndexEntry entry : repo.getIndex().getEntries().values()) {
            String name = entry.path.getFileName().toString();
            entries.add(new TreeEntry(name, "blob", entry.hash));
            staged.put(name, entry.hash);
        }

        String parentHash = repo.getCurrentCommitHash();

        if (!hasChanges(repo, parentHash, staged)) {
            System.out.println("nothing to commit, working tree clean");
            return;
        }

        String treeHash = repo.getObjectStore().storeTree(entries);

        repo.getConfig().load();
        String name = repo.getConfig().get("user.name");
        String email = repo.getConfig().get("user.email");
        String author = (name != null ? name : "Unknown") + " <" + (email != null ? email : "unknown@example.com") + ">";

        String commitHash = repo.getObjectStore().storeCommit(treeHash, parentHash, author, message);
        repo.updateHEAD(commitHash);
    }

    private boolean hasChanges(Repository repo, String parentHash, Map<String, String> staged) throws IOException {
        Map<String, String> committed = new HashMap<>();

        if (parentHash != null) {
            RawObject commitObj = repo.getObjectStore().readObject(parentHash);
            String commitText = new String(commitObj.content, StandardCharsets.UTF_8);
            String header = commitText.substring(0, commitText.indexOf("\n\n"));

            String treeHash = null;
            for (String line : header.split("\n")) {
                int space = line.indexOf(" ");
                if (line.substring(0, space).equals("tree")) {
                    treeHash = line.substring(space + 1);
                }
            }

            for (TreeEntry entry : repo.getObjectStore().readTree(treeHash)) {
                committed.put(entry.name, entry.hash);
            }
        }

        Set<String> allNames = new HashSet<>();
        allNames.addAll(staged.keySet());
        allNames.addAll(committed.keySet());

        for (String name : allNames) {
            String stagedHash = staged.get(name);
            String committedHash = committed.get(name);
            if (stagedHash == null || !stagedHash.equals(committedHash)) {
                return true;
            }
        }

        return false;
    }
}
