package com.minigit.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.minigit.core.Repository;

public class AddCommand implements Command {
    @Override
    public void execute(String[] args) throws Exception {

        Path base = Paths.get(System.getProperty("user.dir"));
        Repository repo = new Repository(base);
        repo.getIndex().load();
        for (String p : args) {
            byte[] rawBytes = Files.readAllBytes(Path.of(p));
            String hash = repo.getObjectStore().storeBlob(rawBytes);
            repo.getIndex().add(Path.of(p), hash);
        }
        repo.getIndex().save();
        
    }
}
