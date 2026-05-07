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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        ProxyExecutable measureAscent = args -> {
            double fontSize = args.length > 0 && args[0].fitsInDouble() ? args[0].asDouble() : 16.0;
            String family = args.length > 1 && args[1].isString() ? args[1].asString() : "sans-serif";
            return JavaTextMetrics.measureAscent(fontSize, family);
        };
        return ProxyObject.fromMap(java.util.Map.of(
                "measureWidth", measureWidth,
                "measureHeight", measureHeight,
                "measureAscent", measureAscent
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

    String normalizeSvgForBatik(String svg) {
        // Suppress empty <rect/> / <rect /> placeholder elements before any other processing.
        // These spacer rects have no attributes and must not gain fill/stroke via CSS inlining,
        // which would make them render as tiny coloured dots in Batik.
        svg = svg.replace("<rect/>", "<rect fill=\"none\" stroke=\"none\"/>")
                 .replace("<rect />", "<rect fill=\"none\" stroke=\"none\"/>");
        String result = resolveCssVariables(replaceRgbaColors(replaceHslColors(svg)))
                .replaceFirst("<svg\\b(?![^>]*xmlns:xlink=)", "<svg xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
                .replaceAll("(?s)<filter\\b[^>]*>.*?</filter>", "")
                .replaceAll("(?s)@keyframes\\s+[^\\{]+\\{.*?\\}\\s*\\}", "")
                .replaceAll("animation:[^;\"}]+;?", "")
                .replaceAll("filter:[^;\"}]+;?", "")
                .replaceAll("\\sfilter=\"url\\(#[^)]+\\)\"", "")
        // Batik does not support !important — strip it so CSS cascade order takes effect
                .replaceAll("\\s*!important", "")
                // Clean up Mermaid's style="undefined...undefined" artifacts on edge paths.
                // Batik ignores CSS class rules when an inline style attribute is present,
                // so malformed/empty style attributes cause paths to render with default fill=black.
                .replaceAll("\\bstyle=\"[\\s;undefined]*\"", "")
                // Mermaid sometimes generates fill="" (empty fill attribute) which makes
                // text invisible. Remove empty fill attributes so CSS can take effect.
                .replaceAll("\\bfill=\"\"", "")
                // Mermaid generates font-weight="" (empty) on class diagram tspans, which
                // blocks inheritance of font-weight: bolder from the parent <g style="...">.
                // Batik treats the empty attribute as present (hasAttribute=true) and stops
                // CSS/style inheritance. Remove it so bold class names render correctly.
                .replaceAll("\\bfont-weight=\"\"", "")
                .replace("orient=\"auto-start-reverse\"", "orient=\"auto\"")
                .replace("alignment-baseline=\"central\"", "alignment-baseline=\"middle\"")
                .replaceAll("<rect([^>]*?)(?<!/) />", "<rect$1></rect>")
                .replaceAll("<rect((?:(?!\\bwidth=)[^>])*)>", "<rect width=\"1\"$1>")
                .replaceAll("<rect((?:(?!\\bheight=)[^>])*)>", "<rect height=\"1\"$1>")
                .replaceAll("<image\\s+href=", "<image xlink:href=")
                .replaceAll("(?s)<image\\b(?![^>]*(?:href|xlink:href)=)[^>]*/>", "")
                .replaceAll("(?s)<image\\b(?![^>]*(?:href|xlink:href)=)[^>]*>.*?</image>", "")
                // Batik picks <foreignObject> inside <switch> even though it can't render HTML.
                // Remove <foreignObject> blocks so <text> fallback gets rendered.
                .replaceAll("(?s)<foreignObject[^>]*>.*?</foreignObject>", "")
                // After removing foreignObject, collapse empty <switch> wrappers
                .replaceAll("<switch>\\s*", "")
                .replaceAll("\\s*</switch>", "");

        // User-journey section/task rects (class="journey-section"/"task task-type-N") have
        // explicit dark fill colors set by Mermaid JS as presentation attributes. In browsers,
        // CSS class rules correctly override these to light theme colors (e.g., #ECECFF), and
        // foreignObject HTML provides the visual section header content. After foreignObject
        // removal we need CSS to set the light background. Remove the explicit fills so our
        // CSS inliner (and Batik's own CSS engine) can apply the correct theme colors.
        result = result.replaceAll(
                "(<rect(?=[^>]*\\bclass=\"[^\"]*(?:journey-section|\\btask\\b)[^\"]*\")[^>]*?)\\bfill=\"#[0-9a-fA-F]+\"([^>]*/?>)",
                "$1$2");
        // The journey-section SVG text fallback (exposed after foreignObject removal) needs
        // a dark fill to remain readable on the light theme-color section background.
        // CSS class rules give it the same light fill as the background rect (invisible).
        // Add fill:#333 to the inline style for section label text elements specifically.
        result = result.replaceAll(
                "(<text(?=[^>]*\\bclass=\"[^\"]*\\bjourney-section\\b)(?![^>]*\\btask\\b)[^>]*?)\\bstyle=\"",
                "$1 style=\"fill:#333;");

        // Batik does not reliably inherit font-weight from a parent <g style="font-weight:…">.
        // Class diagram nodes use <g style="font-weight: bolder" class="label"> around the
        // class-name text, expecting Batik to cascade bold to child tspans. Instead, propagate
        // font-weight="bolder" directly onto every tspan inside such groups.
        result = propagateBolderFontWeight(result);

        // Batik CSS cascade is unreliable for duplicate selectors with conflicting properties.
        // Force correct fill/stroke on <path> and <circle> elements inside <marker> by injecting
        // presentation attributes. This fixes ER relationship line markers (crow's foot symbols).
        result = injectMarkerPresentationAttributes(result);
        // Batik may not apply CSS class rules with compound selectors (#id .class) to edge paths.
        // Inject fill="none" directly on paths that have relationship/edge classes.
        result = injectEdgeFillNone(result);
        // Batik often fails to resolve CSS selectors like `#id .class element` and
        // `#id .classA.classB`. Inline fill/stroke from CSS rules as presentation attributes
        // using DOM-based ancestor class checking for correct specificity.
        result = inlineCssFillStroke(result);
        // After CSS inlining, any <rect class="background"> that STILL has no fill
        // would default to black in Batik. Set fill="none" as a safe fallback.
        result = result.replaceAll(
                "<rect(?=[^>]*\\bclass=\"[^\"]*\\bbackground\\b)(?![^>]*\\bfill=)([^>]*?)(/?>)",
                "<rect fill=\"none\"$1$2");
        return result;
    }

    /**
     * Propagates {@code font-weight="bold"} directly onto {@code <text>} and {@code <tspan>}
     * elements inside {@code <g style="font-weight: bolder" …>} groups. Batik does not reliably
     * cascade font-weight from a group's {@code style} attribute to descendant text elements, and
     * the relative keyword "bolder" may not resolve correctly without an explicit inherited base.
     * Using the absolute keyword "bold" and injecting it on both text and tspan elements ensures
     * Batik picks the correct bold font face.
     */
    String propagateBolderFontWeight(String svg) {
        Pattern groupPattern = Pattern.compile(
                "(?s)(<g\\b(?=[^>]*\\bstyle=\"[^\"]*font-weight:\\s*bolder[^\"]*\")[^>]*>)(.*?)(</g>)");
        Matcher gm = groupPattern.matcher(svg);
        StringBuffer sb = new StringBuffer();
        while (gm.find()) {
            String open = gm.group(1);
            String inner = gm.group(2);
            String close = gm.group(3);
            // Inject font-weight:bold into the style attribute (or add one) on <text>/<tspan>
            // that don't already have font-weight in their style.
            // Also set font-weight="bold" presentation attribute for Batik compatibility.
            Pattern elemPattern = Pattern.compile(
                    "(<(?:text|tspan)(?![^>]*\\bfont-weight=)([^>]*))(>)");
            String patched = elemPattern.matcher(inner).replaceAll(mr -> {
                String elemStart = mr.group(1);
                String closing = mr.group(3);
                // Check if there's an existing style attribute to merge into.
                if (elemStart.contains("style=\"")) {
                    // Prepend font-weight:bold to existing style value.
                    elemStart = elemStart.replaceFirst(
                            "style=\"", "style=\"font-weight:bold;");
                }
                // Also set as presentation attribute.
                return elemStart + " font-weight=\"bold\"" + closing;
            });
            gm.appendReplacement(sb, Matcher.quoteReplacement(open + patched + close));
        }
        gm.appendTail(sb);
        return sb.toString();
    }

    /**
     * Injects fill presentation attributes on {@code <path>} children inside {@code <marker>}
     * elements. Only ER (entity-relationship) markers get {@code fill="none"} (they are line-based
     * crow's foot symbols). Arrow-tip markers (state, flowchart, block, requirement) are left
     * untouched so they render as filled shapes.
     */
    String injectMarkerPresentationAttributes(String svg) {
        Pattern markerPattern = Pattern.compile("(?s)(<marker\\b[^>]*>)(.*?)(</marker>)");
        Matcher m = markerPattern.matcher(svg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String open = m.group(1);
            String body = m.group(2);
            String close = m.group(3);
            // Only inject fill="none" on ER markers (crow's foot line symbols)
            if (open.contains("_er-")) {
                body = body.replaceAll("<path(?![^>]*\\bfill=)([^>]*?)(/?>)",
                        "<path fill=\"none\"$1$2");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(open + body + close));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Adds {@code fill="none"} presentation attribute to SVG {@code <path>} elements
     * that carry Mermaid edge/relationship CSS classes but have no explicit fill attribute.
     * Batik may not resolve compound CSS selectors like {@code #id .class} on edge paths,
     * causing them to render with the default black fill instead of {@code fill:none}.
     */
    String injectEdgeFillNone(String svg) {
        // Match <path> elements that have an edge-related class and no fill attribute yet
        return svg.replaceAll(
                "<path(?=[^>]*\\bclass=\"[^\"]*(?:relationshipLine|edge-thickness|flowchart-link|messageLine)[^\"]*\")(?![^>]*\\bfill=)([^>]*?)(/?>)",
                "<path fill=\"none\"$1$2");
    }

    /**
     * Parses CSS rules from the SVG {@code <style>} block and inlines {@code fill} and
     * {@code stroke} as presentation attributes on matching SVG elements. This compensates
     * for Batik's inability to resolve complex CSS selectors ({@code #id .class element},
     * compound class selectors, etc.).
     *
     * <p>The method handles selectors of the form {@code #id .classA element},
     * {@code #id .classA.classB}, and similar patterns by extracting the required CSS
     * classes and target element type from the last parts of the selector.</p>
     */
    String inlineCssFillStroke(String svg) {
        // Extract CSS from <style> block
        Matcher styleMatcher = Pattern.compile("(?s)<style>(.*?)</style>").matcher(svg);
        if (!styleMatcher.find()) {
            return svg;
        }
        String css = styleMatcher.group(1);

        // Parse CSS rules: extract selector, fill, stroke
        List<CssRule> rules = new ArrayList<>();
        Pattern rulePattern = Pattern.compile("([^{}]+)\\{([^}]+)\\}");
        Matcher ruleMatcher = rulePattern.matcher(css);
        while (ruleMatcher.find()) {
            String selectorGroup = ruleMatcher.group(1).trim();
            String body = ruleMatcher.group(2).trim();

            String fill = extractCssProp(body, "fill");
            String stroke = extractCssProp(body, "stroke");
            String strokeDasharray = extractCssProp(body, "stroke-dasharray");
            String strokeWidth = extractCssProp(body, "stroke-width");
            if (fill == null && stroke == null && strokeDasharray == null && strokeWidth == null) continue;

            for (String sel : selectorGroup.split(",")) {
                sel = sel.trim();
                if (sel.isEmpty()) continue;
                CssRule rule = parseCssSelector(sel, fill, stroke, strokeDasharray, strokeWidth);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        if (rules.isEmpty()) return svg;

        // Parse SVG as DOM to get proper ancestor context
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                    new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));

            // Walk all elements and inject fill/stroke from matching CSS rules
            inlineOnElement(doc.getDocumentElement(), rules);

            // Serialize back to string
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter sw = new java.io.StringWriter();
            transformer.transform(
                    new javax.xml.transform.dom.DOMSource(doc),
                    new javax.xml.transform.stream.StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            // If DOM parsing fails, return SVG unchanged
            return svg;
        }
    }

    private void inlineOnElement(org.w3c.dom.Element element, List<CssRule> rules) {
        String tagName = element.getLocalName();
        if (tagName == null) tagName = element.getTagName();

        // Collect element's own classes
        String classAttr = element.getAttribute("class");
        List<String> classes = new ArrayList<>();
        if (classAttr != null && !classAttr.isEmpty()) {
            for (String c : classAttr.split("\\s+")) {
                if (!c.isEmpty()) classes.add(c);
            }
        }

        // Collect ancestor classes (walk up the tree)
        Set<String> ancestorClasses = new java.util.HashSet<>();
        org.w3c.dom.Node parent = element.getParentNode();
        while (parent instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element pe = (org.w3c.dom.Element) parent;
            String pc = pe.getAttribute("class");
            if (pc != null && !pc.isEmpty()) {
                for (String c : pc.split("\\s+")) {
                    if (!c.isEmpty()) ancestorClasses.add(c);
                }
            }
            parent = parent.getParentNode();
        }

        // Find best matching fill/stroke (last matching rule wins = CSS cascade order)
        String bestFill = null;
        String bestStroke = null;
        String bestStrokeDasharray = null;
        String bestStrokeWidth = null;
        for (CssRule rule : rules) {
            if (rule.matches(tagName, classes, ancestorClasses)) {
                if (rule.fill != null) bestFill = rule.fill;
                if (rule.stroke != null) bestStroke = rule.stroke;
                if (rule.strokeDasharray != null) bestStrokeDasharray = rule.strokeDasharray;
                if (rule.strokeWidth != null) bestStrokeWidth = rule.strokeWidth;
            }
        }

        // Inject as presentation attributes (only if element doesn't already have them)
        if (bestFill != null && !element.hasAttribute("fill")) {
            element.setAttribute("fill", bestFill);
        }
        if (bestStroke != null && !element.hasAttribute("stroke")) {
            element.setAttribute("stroke", bestStroke);
        }
        if (bestStrokeDasharray != null && !element.hasAttribute("stroke-dasharray")) {
            element.setAttribute("stroke-dasharray", bestStrokeDasharray);
        }
        if (bestStrokeWidth != null && !element.hasAttribute("stroke-width")) {
            element.setAttribute("stroke-width", bestStrokeWidth);
        }

        // Recurse into children
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element) {
                inlineOnElement((org.w3c.dom.Element) child, rules);
            }
        }
    }

    private String extractCssProp(String cssBody, String propName) {
        // Match "fill: value" or "stroke: value" — stop at semicolon, closing brace or end
        Pattern p = Pattern.compile("(?:^|;)\\s*" + propName + "\\s*:\\s*([^;}{]+?)\\s*(?:;|$)");
        Matcher m = p.matcher(cssBody);
        String last = null;
        while (m.find()) {
            last = m.group(1).trim();
        }
        return last;
    }

    /**
     * Parses a single CSS selector and extracts:
     * - requiredClasses: CSS classes the element must have
     * - ancestorClasses: CSS classes some ancestor element must have
     * - targetElement: the element type (rect, path, etc.) or null for class-only selectors
     */
    private CssRule parseCssSelector(String selector, String fill, String stroke,
                                     String strokeDasharray, String strokeWidth) {
        // Strip leading #id part (e.g., "#dmtools-mermaid ")
        String sel = selector.replaceFirst("^#[\\w-]+\\s+", "");
        if (sel.startsWith("#")) return null; // pure id selector for a different element

        // Split by whitespace (descendant combinator)
        String[] parts = sel.trim().split("\\s+");
        if (parts.length == 0) return null;

        String lastPart = parts[parts.length - 1];

        // Parse the last part: could be "element", ".class", "element.class", ".classA.classB"
        List<String> requiredClasses = new ArrayList<>();
        String targetElement = null;

        // Extract element name (before first dot, if any)
        if (lastPart.contains(".")) {
            int dotIdx = lastPart.indexOf('.');
            if (dotIdx > 0) {
                targetElement = lastPart.substring(0, dotIdx);
            }
            // Extract all classes from the last part
            for (String cls : lastPart.substring(lastPart.indexOf('.')).split("\\.")) {
                if (!cls.isEmpty()) requiredClasses.add(cls);
            }
        } else {
            targetElement = lastPart;
        }

        // Extract ancestor class requirements from preceding parts
        List<String> ancestorClasses = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            // Extract classes from ancestor selectors
            if (part.contains(".")) {
                for (String cls : part.split("\\.")) {
                    if (!cls.isEmpty() && !cls.startsWith("#")) {
                        ancestorClasses.add(cls);
                    }
                }
            }
        }

        return new CssRule(targetElement, requiredClasses, ancestorClasses, fill, stroke,
                strokeDasharray, strokeWidth);
    }

    static class CssRule {
        final String targetElement; // null = any element
        final List<String> requiredClasses; // classes the element itself must have
        final List<String> ancestorClasses; // classes some ancestor must have
        final String fill;
        final String stroke;
        final String strokeDasharray;
        final String strokeWidth;

        CssRule(String targetElement, List<String> requiredClasses, List<String> ancestorClasses,
                String fill, String stroke, String strokeDasharray, String strokeWidth) {
            this.targetElement = targetElement;
            this.requiredClasses = requiredClasses;
            this.ancestorClasses = ancestorClasses;
            this.fill = fill;
            this.stroke = stroke;
            this.strokeDasharray = strokeDasharray;
            this.strokeWidth = strokeWidth;
        }

        boolean matches(String elemTag, List<String> elemClasses, Set<String> elemAncestorClasses) {
            // Check element type
            if (targetElement != null && !targetElement.equals(elemTag)) return false;
            // Check required classes on the element itself
            for (String rc : requiredClasses) {
                if (!elemClasses.contains(rc)) return false;
            }
            // Check ancestor class requirements
            for (String ac : ancestorClasses) {
                if (!elemAncestorClasses.contains(ac)) return false;
            }
            return true;
        }
    }

    /**
     * Resolves CSS variables in the SVG &lt;style&gt; block. Batik does not support CSS variables,
     * so we inline them before transcoding.
     */
    String resolveCssVariables(String svg) {
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

    String replaceHslColors(String svg) {
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

    /**
     * Converts CSS {@code rgba(r, g, b, a)} colors to hex. Batik does not support rgba()
     * in CSS or presentation attributes. The alpha channel is dropped (premultiplied against white).
     */
    String replaceRgbaColors(String svg) {
        Pattern pattern = Pattern.compile(
                "rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([\\d.]+)\\s*\\)");
        Matcher matcher = pattern.matcher(svg);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int r = Integer.parseInt(matcher.group(1));
            int g = Integer.parseInt(matcher.group(2));
            int b = Integer.parseInt(matcher.group(3));
            double a = Double.parseDouble(matcher.group(4));
            // Premultiply alpha against white background
            int rr = (int) Math.round(r * a + 255 * (1 - a));
            int gg = (int) Math.round(g * a + 255 * (1 - a));
            int bb = (int) Math.round(b * a + 255 * (1 - a));
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(String.format("#%02X%02X%02X", rr, gg, bb)));
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
