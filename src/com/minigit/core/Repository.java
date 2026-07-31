package com.minigit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Repository {
    Path workingDir;
    Path gitDir;
    ObjectStore objectStore;

    public Repository(Path workingDir) {
        this.workingDir = workingDir;
        this.gitDir = workingDir.resolve(".git");
        this.objectStore = new ObjectStore(gitDir);
    }
    

    private Path getCurrentBranchRefPath() throws IOException {
        Path headPath = gitDir.resolve("HEAD");
        String head = Files.readString(headPath).strip();

        if (!head.startsWith("ref: ")) {
            return null;
        }

        String ref = head.substring(5);

        return gitDir.resolve(ref);
    }

    public String getCurrentCommitHash() throws IOException {
        Path refPath = getCurrentBranchRefPath();

        if (!Files.exists(refPath)) {
            return null;
        }

        String commitHash = Files.readString(refPath).strip();
        return commitHash;
    }

    public void updateHEAD(String commitHash) throws IOException {
        Path refFile = getCurrentBranchRefPath();
        Files.writeString(refFile, commitHash + "\n");
    }

}
