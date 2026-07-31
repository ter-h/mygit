package com.minigit.commands;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.File;
import java.io.IOException;
import java.io.*;

public class InitCommand implements Command {

    @Override
    public void execute(String[] args) throws Exception {

        Path base = Paths.get(System.getProperty("user.dir"));
        Path gitPath = base.resolve(".git");

        if (Files.isDirectory(gitPath)) {
            System.out.println("Reinitialized existing Git repository in " + gitPath);
            return;
        }

        Files.createDirectories(gitPath);
        
        Path objects = gitPath.resolve("objects");

        Files.createDirectories(objects);
        
        Path refs = gitPath.resolve("refs");
        Path heads = refs.resolve("heads");
        Files.createDirectories(heads);

        Path tags = refs.resolve("tags");
        Files.createDirectories(tags);
        
        String content = "ref: refs/heads/main\n";

        byte[] contentByte = content.getBytes(StandardCharsets.UTF_8);
        Files.write(gitPath.resolve("HEAD"), contentByte);
        Files.write(gitPath.resolve("config"), new byte[0]);
        Files.write(gitPath.resolve("index"), new byte[0]);

        
    }
}