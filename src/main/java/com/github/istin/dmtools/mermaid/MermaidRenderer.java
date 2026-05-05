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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
                    return renderFunction.execute(definition).asString();
                } catch (PolyglotException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }
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
        PNGTranscoder transcoder = new PNGTranscoder();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            transcoder.transcode(new TranscoderInput(inputStream), new TranscoderOutput(outputStream));
        }
    }
}
