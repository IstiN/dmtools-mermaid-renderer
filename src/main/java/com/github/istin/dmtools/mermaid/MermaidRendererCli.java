package com.github.istin.dmtools.mermaid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MermaidRendererCli {

    private MermaidRendererCli() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(execute(args));
    }

    public static Path execute(String[] args) throws Exception {
        Request request = parse(args);
        MermaidRenderer renderer = new MermaidRenderer();
        return switch (request.command()) {
            case "mermaid_to_svg" -> renderer.renderToSvgFile(request.definition(), request.outputPath());
            case "mermaid_to_png" -> renderer.renderToPng(request.definition(), request.outputPath());
            default -> throw new IllegalArgumentException("Unsupported command: " + request.command());
        };
    }

    static Request parse(String[] args) throws Exception {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException(usage());
        }

        String command = args[0];
        if (!"mermaid_to_svg".equals(command) && !"mermaid_to_png".equals(command)) {
            throw new IllegalArgumentException("Unsupported command: " + command + ". " + usage());
        }

        StringBuilder definition = new StringBuilder();
        Path outputPath = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--output".equals(arg) || "-o".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + arg + ". " + usage());
                }
                outputPath = Paths.get(args[++i]);
            } else if ("--file".equals(arg) || "-f".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + arg + ". " + usage());
                }
                definition.append(Files.readString(Paths.get(args[++i])));
            } else {
                if (!definition.isEmpty()) {
                    definition.append(' ');
                }
                definition.append(arg);
            }
        }

        String diagram = definition.toString().trim();
        if (diagram.isEmpty()) {
            throw new IllegalArgumentException("Mermaid diagram text is required. " + usage());
        }

        return new Request(command, diagram, outputPath);
    }

    private static String usage() {
        return "Usage: mermaid_to_svg|mermaid_to_png \"flowchart TD; A[Start] --> B[Done]\" [--output output.svg|png]";
    }

    record Request(String command, String definition, Path outputPath) {
    }
}
