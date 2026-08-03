package com.minigit.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ObjectStore {
    private Path objectsDir;
    
    public ObjectStore(Path gitDir) {
        this.objectsDir = gitDir.resolve("objects");
    }

    /* It takes an existing byte array, 
    hashes it using SHA-1, then converts the resulting hash 
    bytes into a hexadecimal string.*/
    public static String sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /* Build header and combine with content, 
        hash the combined bytes, 
        split hash into subdir/filename,
        create the subdirectory if missing, 
        write the combined bytes to that file
    */
    private static byte[] buildFullData(String type, byte[] content) throws IOException {
        String header = type + " " + content.length + "\0";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(header.getBytes(StandardCharsets.UTF_8));
        buffer.write(content);

        return buffer.toByteArray();
    }

    public static String hashObject(String type, byte[] content) throws IOException {
        return sha1(buildFullData(type, content));
    }

    public String storeBlob(byte[] bytes) throws IOException {
        byte[] fullData = buildFullData("blob", bytes);
        String hash = sha1(fullData);

        Path objectFile = objectsDir.resolve(hash.substring(0, 2))
                            .resolve(hash.substring(2));
        Path parent = objectFile.getParent();
        Files.createDirectories(parent);
        try (OutputStream out = Files.newOutputStream(objectFile)) {
            out.write(fullData);
        }

        return hash;
    }

    /* read byte data one by one,
        determine where null character is,
        copy header bytes from 0 - nullIndex to,
        extract type and size,
        content is nullIndex + 1 to end
     */
    public static RawObject extractRawObject(byte[] fullData) {
        int nullIndex = -1;

        for (int i = 0; i < fullData.length; i++) {
            if (fullData[i] == 0) {
                nullIndex = i;
                break;
            }
        }

        byte[] headerBytes = Arrays.copyOfRange(fullData, 0, nullIndex);

        String header = new String(headerBytes, StandardCharsets.UTF_8);
        String[] parts = header.split(" ");

        String type = parts[0];
        int size = Integer.parseInt(parts[1]);
        
        byte[] content = Arrays.copyOfRange(
                fullData,
                nullIndex + 1,
                fullData.length
        );

        return new RawObject(type, content);
    }

    /* Read hash and return Raw object of type and content,
        find path using first two characters of hash,
        read all bytes, extract the content and header into raw object.
    
    */
    public RawObject readObject(String hash) throws IOException {
        Path objectFile = objectsDir.resolve(hash.substring(0, 2))
                                .resolve(hash.substring(2));
        byte[] rawBytes = Files.readAllBytes(objectFile);
        RawObject res = extractRawObject(rawBytes);

        return res;
    }

    public static class RawObject {
        public final String type;
        public final byte[] content;

        public RawObject(String type, byte[] content) {
            this.type = type;
            this.content = content;
        }
    }

    public static class TreeEntry {
        public final String name;
        public final String type;
        public final String hash;

        public TreeEntry(String name, String type, String hash) {
            this.name = name;
            this.type = type;
            this.hash = hash;
        }

    }

    public String storeTree(List<TreeEntry> entries) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        for (TreeEntry e : entries) {

            contentBuilder.append(e.name)
            .append(' ')
            .append(e.type)
            .append(' ')
            .append(e.hash)
            .append('\n');  
        }        

        byte[] content = contentBuilder.toString().getBytes(StandardCharsets.UTF_8);
        
        String header = "tree " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(headerBytes);
        buffer.write(content);

        byte[] fullData = buffer.toByteArray();

        String hash = sha1(fullData);

        Path objectFile = objectsDir.resolve(hash.substring(0, 2))
        .resolve(hash.substring(2));
        Path parent = objectFile.getParent();
        Files.createDirectories(parent);
        
        try (OutputStream out = Files.newOutputStream(objectFile)) {
            out.write(fullData);
        }

        return hash;
    }

    public List<TreeEntry> readTree(String treeHash) throws IOException {
        RawObject obj = readObject(treeHash);
        String content = new String(obj.content,StandardCharsets.UTF_8);
        List<TreeEntry> entries = new ArrayList<>();

        for (String line : content.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split(" ");
            entries.add(new TreeEntry(parts[0], parts[1], parts[2]));
        }

        return entries;
    }


    public String storeCommit(String treeHash, String parentHash,  String author, String message) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("tree ")
        .append(treeHash)
        .append(parentHash != null ? "\nparent " + parentHash : "")
        .append("\nauthor ")
        .append(author)
        .append("\ncommitter ")
        .append(author)
        .append("\n\n")
        .append(message);

        byte[] content = contentBuilder.toString().getBytes(StandardCharsets.UTF_8);

        String header = "commit " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(headerBytes);
        buffer.write(content);

        byte[] fullData = buffer.toByteArray();

        String hash = sha1(fullData);

        Path objectFile = objectsDir.resolve(hash.substring(0, 2))
        .resolve(hash.substring(2));
        Path parent = objectFile.getParent();
        Files.createDirectories(parent);
        
        try (OutputStream out = Files.newOutputStream(objectFile)) {
            out.write(fullData);
        }

        return hash;


    }


}
