package com.example.nunki.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class CliRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "cli".equalsIgnoreCase(args[0])) {
            System.out.println("Nunki running in CLI mode");
            System.out.println("Arguments: " + Arrays.toString(args));
            // Add CLI logic here
            // System.exit(0); // Optional: exit if purely a CLI task
        }
    }
}