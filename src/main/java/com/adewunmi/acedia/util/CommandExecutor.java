package com.adewunmi.acedia.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class CommandExecutor {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("nux") || OS_NAME.contains("nix");
    }

    /**
     * Executes a command and returns the exit code
     */
    public static int executeCommand(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder();

        if (isWindows()) {
            processBuilder.command("cmd.exe", "/c", command);
        } else if (isMac() || isLinux()) {
            processBuilder.command("/bin/sh", "-c", command);
        } else {
            throw new UnsupportedOperationException("Unsupported OS platform");
        }

        try {
            Process process = processBuilder.start();

            // Read output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }

            // Read error output
            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                log.error(line);
            }

            int exitCode = process.waitFor();
            return exitCode;

        } catch (IOException | InterruptedException e) {
            log.error("Failed to execute command: {}", command, e);
            return -1;
        }
    }
}
