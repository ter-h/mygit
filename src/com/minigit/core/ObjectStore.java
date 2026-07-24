package com.minigit.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


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

    /* Build header and combine with content, hash the combined bytes, split hash into subdir/filename,
        create the subdirectory if missing, write the combined bytes to that file
    */
    public String storeBlob(byte[] bytes) throws IOException {
        String header = "blob " + bytes.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(headerBytes);
        buffer.write(bytes);

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

    public static RawObject rawObject(byte[] fullData) {
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

    public RawObject readObject(String hash) throws IOException {
        Path objectFile = objectsDir.resolve(hash.substring(0, 2))
                                .resolve(hash.substring(2));
        byte[] rawBytes = Files.readAllBytes(objectFile);
        RawObject res = rawObject(rawBytes);

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



}
