package com.minigit;

import com.minigit.commands.*;

public class MiniGit {
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                printUsage();
                return;
            }
        

            String command = args[0];
            String[] commandArgs = new String[args.length - 1];
            System.arraycopy(args, 1, commandArgs, 0, args.length - 1);

            switch (command) {
                case "init":
                    InitCommand.execute(commandArgs);
                    break;
                case "add":
                    AddCommand.execute(commandArgs);
                    break;
                case "commit":
                    CommitCommand.execute(commandArgs);
                    break;
                case "log":
                    LogCommand.execute(commandArgs);
                    break;
                case "status":
                    StatusCommand.execute(commandArgs);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    printUsage();
                    break;
            }
             
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
        

    private static void printUsage() {
        System.out.println("MiniGit - A simplified git implementation");
        System.out.println("\nUsage:");
        System.out.println("  minigit init                    Initialize a new repository");
        System.out.println("  minigit add <files...>          Stage files");
        System.out.println("  minigit commit -m <message>     Create a commit");
        System.out.println("  minigit log                     Show commit history");
        System.out.println("  minigit status                  Show working directory status");
    }
}