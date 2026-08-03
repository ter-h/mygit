package com.minigit.commands;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.minigit.core.Repository;

public class ConfigCommand implements Command {
    @Override
    public void execute(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: minigit config <key> <value>");
            return;
        }
        String key = args[0];
        String value = args[1];   
        Path base = Paths.get(System.getProperty("user.dir"));
        Repository repo = new Repository(base);
        repo.getConfig().load();
        repo.getConfig().set(key, value);
        repo.getConfig().save();
    }
    
}
