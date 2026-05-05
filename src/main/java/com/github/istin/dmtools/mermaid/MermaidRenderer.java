package com.github.istin.dmtools.mermaid;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class MermaidRenderer {

    private static final String RENDERER_RESOURCE = "/mermaid/mermaid-renderer.js";

    public String renderToSvg(String definition) throws IOException {
        if (definition == null || definition.trim().isEmpty()) {
            throw new IllegalArgumentException("Mermaid definition is required");
        }

        try (InputStream stream = getClass().getResourceAsStream(RENDERER_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Mermaid renderer resource is missing: " + RENDERER_RESOURCE);
            }

            Source source = Source.newBuilder(
                    "js",
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    "mermaid-renderer.js"
            ).build();

            try (Context context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowHostClassLookup(className -> false)
                    .allowIO(false)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build()) {
                context.eval(source);
                Value renderFunction = context.getBindings("js").getMember("renderMermaidToSvg");
                if (renderFunction == null || !renderFunction.canExecute()) {
                    throw new IllegalStateException("Mermaid renderer did not expose renderMermaidToSvg");
                }
                try {
                    Value result = renderFunction.execute(definition);
                    return resolveStringResult(context, result);
                } catch (PolyglotException e) {
                    throw new IllegalArgumentException(formatPolyglotError(e), e);
                }
            }
        }
    }

    private String formatPolyglotError(PolyglotException e) {
        StringBuilder message = new StringBuilder(e.getMessage() == null ? "Mermaid rendering failed" : e.getMessage());
        int frameCount = 0;
        for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
            if (frameCount++ >= 6) {
                break;
            }
            message.append(" at ").append(frame);
        }
        return message.toString();
    }

    private String resolveStringResult(Context context, Value result) {
        if (result.isString()) {
            return result.asString();
        }
        if (!result.hasMember("then")) {
            throw new IllegalStateException("Mermaid renderer returned a non-string result: " + result);
        }

        AtomicReference<String> resolved = new AtomicReference<>();
        AtomicReference<RuntimeException> rejected = new AtomicReference<>();
        result.invokeMember("then",
                (ProxyExecutable) args -> {
                    resolved.set(args.length > 0 ? args[0].asString() : "");
                    return null;
                },
                (ProxyExecutable) args -> {
                    String message = args.length > 0 ? args[0].toString() : "Unknown Mermaid rendering error";
                    rejected.set(new IllegalArgumentException(message));
                    return null;
                });
        for (int i = 0; i < 1024 && resolved.get() == null && rejected.get() == null; i++) {
            context.eval("js", "globalThis.__dmtoolsDrainTimers?.(); Promise.resolve();");
        }
        if (rejected.get() != null) {
            throw rejected.get();
        }
        if (resolved.get() == null) {
            throw new IllegalStateException("Mermaid renderer promise did not resolve synchronously");
        }
        return resolved.get();
    }

    public Path renderToSvgFile(String definition, Path outputPath) throws IOException {
        Path targetPath = resolveOutputPath(outputPath, ".svg");
        Files.writeString(targetPath, renderToSvg(definition), StandardCharsets.UTF_8);
        return assertWritten(targetPath, "SVG");
    }

    public Path renderToPng(String definition, Path outputPath) throws IOException, TranscoderException {
        Path targetPath = resolveOutputPath(outputPath, ".png");
        convertSvgToPng(renderToSvg(definition), targetPath);
        return assertWritten(targetPath, "PNG");
    }

    private Path resolveOutputPath(Path outputPath, String suffix) throws IOException {
        Path targetPath = outputPath != null ? outputPath : Files.createTempFile("dmtools-mermaid-", suffix);
        Path parent = targetPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return targetPath.toAbsolutePath().normalize();
    }

    private Path assertWritten(Path targetPath, String type) throws IOException {
        if (!Files.exists(targetPath) || Files.size(targetPath) == 0) {
            throw new IOException(type + " renderer produced an empty file: " + targetPath);
        }
        return targetPath;
    }

    private void convertSvgToPng(String svg, Path outputPath) throws IOException, TranscoderException {
        String normalizedSvg = normalizeSvgForBatik(svg);
        PNGTranscoder transcoder = new PNGTranscoder();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(normalizedSvg.getBytes(StandardCharsets.UTF_8));
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            TranscoderInput input = new TranscoderInput(inputStream);
            input.setURI(outputPath.toUri().toString());
            transcoder.transcode(input, new TranscoderOutput(outputStream));
        }
    }

    private String normalizeSvgForBatik(String svg) {
        return svg
                .replaceFirst("<svg\\b(?![^>]*xmlns:xlink=)", "<svg xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
                .replaceAll("(?s)<filter\\b[^>]*>.*?</filter>", "")
                .replaceAll("(?s)@keyframes\\s+[^\\{]+\\{.*?\\}\\s*\\}", "")
                .replaceAll("animation:[^;\"}]+;?", "")
                .replaceAll("filter:[^;\"}]+;?", "")
                .replaceAll("\\sfilter=\"url\\(#[^)]+\\)\"", "")
                .replace("orient=\"auto-start-reverse\"", "orient=\"auto\"")
                .replace("alignment-baseline=\"central\"", "alignment-baseline=\"middle\"")
                .replaceAll("<rect([^>]*?)(?<!/) />", "<rect$1></rect>")
                .replaceAll("<rect((?:(?!\\bwidth=)[^>])*)>", "<rect width=\"1\"$1>")
                .replaceAll("<rect((?:(?!\\bheight=)[^>])*)>", "<rect height=\"1\"$1>")
                .replaceAll("<image\\s+href=", "<image xlink:href=")
                .replaceAll("(?s)<image\\b(?![^>]*(?:href|xlink:href)=)[^>]*/>", "")
                .replaceAll("(?s)<image\\b(?![^>]*(?:href|xlink:href)=)[^>]*>.*?</image>", "");
    }
}
