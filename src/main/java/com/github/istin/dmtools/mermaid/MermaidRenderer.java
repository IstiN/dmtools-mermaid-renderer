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

import org.graalvm.polyglot.proxy.ProxyObject;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MermaidRenderer {

    private static final String RENDERER_RESOURCE = "/mermaid/mermaid-renderer.js";
    private static final Pattern VIEW_BOX_PATTERN = Pattern.compile("\\bviewBox=\"\\s*[-\\d.]+\\s+[-\\d.]+\\s+([\\d.]+)\\s+([\\d.]+)\\s*\"");
    private static final float MAX_PNG_SIDE = 2400f;

    public String renderToSvg(String definition) throws IOException {
        MermaidDiagramSupport.requireProductionSupport(definition);
        return renderToSvgUnchecked(definition);
    }

    String renderToSvgUnchecked(String definition) throws IOException {
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
                    ProxyObject javaMetrics = buildJavaMetricsProxy();
                    Value result = renderFunction.execute(definition, javaMetrics);
                    return resolveStringResult(context, result);
                } catch (PolyglotException e) {
                    throw new IllegalArgumentException(formatPolyglotError(e), e);
                }
            }
        }
    }

    /** Exposes JavaTextMetrics to GraalJS as a plain JS object with measureWidth/measureHeight. */
    private static ProxyObject buildJavaMetricsProxy() {
        ProxyExecutable measureWidth = args -> {
            String text = args.length > 0 && args[0].isString() ? args[0].asString() : "";
            double fontSize = args.length > 1 && args[1].fitsInDouble() ? args[1].asDouble() : 16.0;
            String family = args.length > 2 && args[2].isString() ? args[2].asString() : "sans-serif";
            return JavaTextMetrics.measureWidth(text, fontSize, family);
        };
        ProxyExecutable measureHeight = args -> {
            double fontSize = args.length > 0 && args[0].fitsInDouble() ? args[0].asDouble() : 16.0;
            String family = args.length > 1 && args[1].isString() ? args[1].asString() : "sans-serif";
            return JavaTextMetrics.measureHeight(fontSize, family);
        };
        return ProxyObject.fromMap(java.util.Map.of(
                "measureWidth", measureWidth,
                "measureHeight", measureHeight
        ));
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

    Path renderToSvgFileUnchecked(String definition, Path outputPath) throws IOException {
        Path targetPath = resolveOutputPath(outputPath, ".svg");
        Files.writeString(targetPath, renderToSvgUnchecked(definition), StandardCharsets.UTF_8);
        return assertWritten(targetPath, "SVG");
    }

    public Path renderToPng(String definition, Path outputPath) throws IOException, TranscoderException {
        Path targetPath = resolveOutputPath(outputPath, ".png");
        convertSvgToPng(renderToSvg(definition), targetPath);
        return assertWritten(targetPath, "PNG");
    }

    Path renderToPngUnchecked(String definition, Path outputPath) throws IOException, TranscoderException {
        Path targetPath = resolveOutputPath(outputPath, ".png");
        convertSvgToPng(renderToSvgUnchecked(definition), targetPath);
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
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
        float[] dimensions = resolvePngDimensions(normalizedSvg);
        if (dimensions != null) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, dimensions[0]);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, dimensions[1]);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(normalizedSvg.getBytes(StandardCharsets.UTF_8));
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            TranscoderInput input = new TranscoderInput(inputStream);
            input.setURI(outputPath.toUri().toString());
            transcoder.transcode(input, new TranscoderOutput(outputStream));
        }
    }

    private String normalizeSvgForBatik(String svg) {
        return resolveCssVariables(replaceHslColors(svg))
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

    /**
     * Resolves CSS custom properties (var(--name)) using values declared in the SVG's own
     * &lt;style&gt; block. Batik does not support CSS variables, so we inline them before transcoding.
     */
    private String resolveCssVariables(String svg) {
        // Extract all --variable: value declarations from the style block
        java.util.Map<String, String> vars = new java.util.LinkedHashMap<>();
        Pattern declPattern = Pattern.compile("--([\\w-]+)\\s*:\\s*([^;}{]+?)\\s*(?:;|})");
        Matcher decl = declPattern.matcher(svg);
        while (decl.find()) {
            vars.put("--" + decl.group(1), decl.group(2).trim());
        }
        if (vars.isEmpty()) {
            return svg;
        }
        // Replace var(--name) and var(--name, fallback) with resolved values
        Pattern usePattern = Pattern.compile("var\\(\\s*(--[\\w-]+)\\s*(?:,\\s*([^)]+))?\\s*\\)");
        Matcher use = usePattern.matcher(svg);
        StringBuffer result = new StringBuffer();
        while (use.find()) {
            String varName = use.group(1);
            String fallback = use.group(2);
            String resolved = vars.get(varName);
            if (resolved == null && fallback != null) {
                resolved = fallback.trim();
            }
            if (resolved == null) {
                resolved = "#cccccc";
            }
            // Recursively resolve nested vars (one level)
            Matcher nested = usePattern.matcher(resolved);
            if (nested.find()) {
                String nestedVar = nested.group(1);
                String nestedFallback = nested.group(2);
                String nestedResolved = vars.getOrDefault(nestedVar, nestedFallback != null ? nestedFallback.trim() : "#cccccc");
                resolved = nested.replaceAll(Matcher.quoteReplacement(nestedResolved));
            }
            use.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        use.appendTail(result);
        return result.toString();
    }

    private String replaceHslColors(String svg) {
        Pattern pattern = Pattern.compile("hsl\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)%\\s*\\)");
        Matcher matcher = pattern.matcher(svg);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            double hue = Double.parseDouble(matcher.group(1));
            double saturation = Double.parseDouble(matcher.group(2)) / 100.0;
            double lightness = Double.parseDouble(matcher.group(3)) / 100.0;
            matcher.appendReplacement(result, Matcher.quoteReplacement(hslToHex(hue, saturation, lightness)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String hslToHex(double hue, double saturation, double lightness) {
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double normalizedHue = ((hue % 360) + 360) % 360 / 60.0;
        double x = chroma * (1 - Math.abs(normalizedHue % 2 - 1));
        double red = 0;
        double green = 0;
        double blue = 0;
        if (normalizedHue < 1) {
            red = chroma;
            green = x;
        } else if (normalizedHue < 2) {
            red = x;
            green = chroma;
        } else if (normalizedHue < 3) {
            green = chroma;
            blue = x;
        } else if (normalizedHue < 4) {
            green = x;
            blue = chroma;
        } else if (normalizedHue < 5) {
            red = x;
            blue = chroma;
        } else {
            red = chroma;
            blue = x;
        }
        double match = lightness - chroma / 2;
        return String.format("#%02X%02X%02X",
                Math.round((red + match) * 255),
                Math.round((green + match) * 255),
                Math.round((blue + match) * 255));
    }

    private float[] resolvePngDimensions(String svg) {
        Matcher matcher = VIEW_BOX_PATTERN.matcher(svg);
        if (!matcher.find()) {
            return null;
        }
        float width = Float.parseFloat(matcher.group(1));
        float height = Float.parseFloat(matcher.group(2));
        if (width <= 0 || height <= 0) {
            return null;
        }
        // Scale down only if the diagram exceeds max side — never force upscaling
        float scale = Math.min(MAX_PNG_SIDE / Math.max(width, height), 1f);
        return new float[]{Math.max(1f, width * scale), Math.max(1f, height * scale)};
    }
}
