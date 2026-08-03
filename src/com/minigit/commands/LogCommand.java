package com.minigit.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.minigit.core.ObjectStore;
import com.minigit.core.Repository;

public class LogCommand implements Command {
    @Override
    public void execute(String[] args) throws IOException {
        Path base = Paths.get(System.getProperty("user.dir"));
        Repository repo = new Repository(base);
        
        String currentHash = repo.getCurrentCommitHash();
        
        if (currentHash == null) {
            System.out.println("No commits yet");
            return;
        }

        while (currentHash != null) {
            ObjectStore.RawObject obj = repo.getObjectStore().readObject(currentHash);
            String commitText = new String(
                obj.content,
                StandardCharsets.UTF_8
            );

            int separator = commitText.indexOf("\n\n");
            String header = commitText.substring(0, separator);
            String message = commitText.substring(separator + 2);
            String[] lines = header.split("\n");

            String parentHash = null;

            for (String line : lines) {

                int space = line.indexOf(" ");
                String key = line.substring(0, space);
                String value = line.substring(space + 1);

                if (key.equals("parent")) {
                    parentHash = value;
                }
            }

            System.out.println("commit " + currentHash);
            System.out.println(message);
            System.out.println();

            currentHash = parentHash;

        }

    }
}
