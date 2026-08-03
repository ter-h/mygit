package com.minigit.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.minigit.core.Index.IndexEntry;
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

        for (IndexEntry entry : repo.getIndex().getEntries().values()) {
            TreeEntry treeEntry = new TreeEntry(
                entry.path.getFileName().toString(),
                "blob",
                entry.hash
            );

            entries.add(treeEntry);
        }

        String treeHash = repo.getObjectStore().storeTree(entries);

        String parentHash = repo.getCurrentCommitHash();
        String author = "Unknown <unknown@example.com>";        // flag: update later

        String commitHash = repo.getObjectStore().storeCommit(treeHash, parentHash, author, message);
        repo.updateHEAD(commitHash);
        repo.getIndex().clear();
        repo.getIndex().save();

        
    }
}
