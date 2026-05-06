package com.github.istin.dmtools.mermaid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SVG normalization pipeline in {@link MermaidRenderer}.
 * Each nested class covers a specific normalization step so that regressions
 * in one area do not silently break others.
 */
class NormalizeSvgForBatikTest {

    private MermaidRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MermaidRenderer();
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private static String svgWrap(String style, String body) {
        return "<svg id=\"dmtools-mermaid\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 100\">"
                + "<style>" + style + "</style>"
                + body
                + "</svg>";
    }

    private static String svgWrap(String body) {
        return svgWrap("", body);
    }

    // ── !important stripping ──────────────────────────────────────────────
    @Nested
    class ImportantStripping {
        @Test
        void stripsImportantFromCssProperty() {
            String svg = svgWrap(".marker{fill:none !important;}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("!important"), "!important should be stripped");
            assertTrue(result.contains("fill:none"), "fill value should be preserved");
        }

        @Test
        void stripsMultipleImportantDeclarations() {
            String svg = svgWrap(".a{fill:red !important;stroke:blue !important;}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("!important"));
            assertTrue(result.contains("fill:red"));
            assertTrue(result.contains("stroke:blue"));
        }
    }

    // ── style="undefined" cleanup ─────────────────────────────────────────
    @Nested
    class UndefinedStyleCleanup {
        @Test
        void removesStyleWithOnlyUndefined() {
            String svg = svgWrap("<path style=\"undefined;;;undefined\" d=\"M0,0L10,10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("style=\"undefined"), "malformed style attribute should be removed");
        }

        @Test
        void removesEmptyStyleAttribute() {
            String svg = svgWrap("<path style=\"  ;; \" d=\"M0,0L10,10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("style=\"  ;; \""));
        }

        @Test
        void preservesValidStyleAttribute() {
            String svg = svgWrap("<path style=\"stroke: none\" d=\"M0,0L10,10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("stroke: none"), "valid style attribute should be preserved");
        }
    }

    // ── empty fill attribute cleanup ─────────────────────────────────────
    @Nested
    class EmptyFillCleanup {
        @Test
        void removesEmptyFillAttribute() {
            String svg = svgWrap("<text fill=\"\" class=\"taskText\">Hello</text>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("fill=\"\""), "empty fill=\"\" should be removed");
        }

        @Test
        void preservesNonEmptyFillAttribute() {
            String svg = svgWrap("<text fill=\"#333\" class=\"taskText\">Hello</text>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"#333\""), "non-empty fill should be preserved");
        }
    }

    // ── orient="auto-start-reverse" fix ───────────────────────────────────
    @Nested
    class OrientFix {
        @Test
        void replacesAutoStartReverseWithAuto() {
            String svg = svgWrap("<marker orient=\"auto-start-reverse\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("orient=\"auto\""));
            assertFalse(result.contains("auto-start-reverse"));
        }
    }

    // ── self-closing rect normalization ────────────────────────────────────
    @Nested
    class RectNormalization {
        @Test
        void convertsSelfClosingRectToOpenCloseTag() {
            String svg = svgWrap("<rect width=\"50\" height=\"30\" />");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("</rect>"), "self-closing <rect .../> should become <rect...></rect>");
        }

        @Test
        void addsDefaultWidthWhenMissing() {
            String svg = svgWrap("<rect height=\"30\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("width=\""), "missing width should be injected");
        }

        @Test
        void addsDefaultHeightWhenMissing() {
            String svg = svgWrap("<rect width=\"50\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("height=\""), "missing height should be injected");
        }

        @Test
        void preservesExistingWidthAndHeight() {
            String svg = svgWrap("<rect width=\"50\" height=\"30\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("width=\"50\""));
            assertTrue(result.contains("height=\"30\""));
        }
    }

    // ── marker presentation attribute injection ───────────────────────────
    @Nested
    class MarkerPresentationAttributes {
        @Test
        void injectsFillNoneOnErMarkerPaths() {
            String svg = svgWrap(
                    "<defs><marker id=\"dmtools-mermaid_er-oneOrMany\"><path d=\"M0,0L5,5L0,10\"/></marker></defs>"
                    + "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.injectMarkerPresentationAttributes(svg);
            assertTrue(result.contains("fill=\"none\""), "ER marker paths should get fill=\"none\"");
        }

        @Test
        void doesNotInjectFillOnNonErMarkerPaths() {
            String svg = svgWrap(
                    "<defs><marker id=\"flowchart-pointEnd\"><path d=\"M0,0L5,5L0,10\"/></marker></defs>"
                    + "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.injectMarkerPresentationAttributes(svg);
            assertFalse(result.contains("fill=\"none\""), "non-ER marker paths should NOT get fill=\"none\"");
        }

        @Test
        void doesNotOverrideExistingFillOnErMarkerPath() {
            String svg = svgWrap(
                    "<defs><marker id=\"dmtools-mermaid_er-zeroOrOne\"><path fill=\"white\" d=\"M0,0L5,5\"/></marker></defs>"
                    + "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.injectMarkerPresentationAttributes(svg);
            assertTrue(result.contains("fill=\"white\""), "existing fill should not be replaced");
            assertEquals(1, countOccurrences(result, "fill=\"white\""));
        }

        @Test
        void handlesMultipleMarkersMixedErAndNonEr() {
            String svg = svgWrap(
                    "<defs>"
                    + "<marker id=\"dmtools-mermaid_er-oneOrMany\"><path d=\"M0,0L5,5\"/></marker>"
                    + "<marker id=\"flowchart-pointEnd\"><path d=\"M0,0L10,10\"/></marker>"
                    + "</defs><rect width=\"10\" height=\"10\"/>");
            String result = renderer.injectMarkerPresentationAttributes(svg);
            assertEquals(1, countOccurrences(result, "fill=\"none\""),
                    "only ER marker path should get fill=\"none\"");
        }
    }

    // ── edge fill=none injection ──────────────────────────────────────────
    @Nested
    class EdgeFillNone {
        @Test
        void injectsFillNoneOnRelationshipLinePaths() {
            String svg = svgWrap(
                    "<path class=\"edge-thickness-normal relationshipLine\" d=\"M0,0L10,10\"/>");
            String result = renderer.injectEdgeFillNone(svg);
            assertTrue(result.contains("fill=\"none\""), "edge paths should get fill=\"none\"");
        }

        @Test
        void injectsFillNoneOnFlowchartLinkPaths() {
            String svg = svgWrap(
                    "<path class=\"flowchart-link edge-thickness-normal\" d=\"M0,0L10,10\"/>");
            String result = renderer.injectEdgeFillNone(svg);
            assertTrue(result.contains("fill=\"none\""));
        }

        @Test
        void doesNotInjectOnNonEdgePaths() {
            String svg = svgWrap("<path class=\"some-other-class\" d=\"M0,0L10,10\"/>");
            String result = renderer.injectEdgeFillNone(svg);
            assertFalse(result.contains("fill=\"none\""), "non-edge paths should not get fill=\"none\"");
        }

        @Test
        void doesNotOverrideExistingFillOnEdgePaths() {
            String svg = svgWrap(
                    "<path class=\"flowchart-link\" fill=\"red\" d=\"M0,0L10,10\"/>");
            String result = renderer.injectEdgeFillNone(svg);
            assertTrue(result.contains("fill=\"red\""), "existing fill should not be replaced");
            assertFalse(result.contains("fill=\"none\""));
        }
    }

    // ── background rect fill=none injection ───────────────────────────────
    @Nested
    class BackgroundRectFill {
        @Test
        void injectsFillNoneOnBackgroundRects() {
            String svg = svgWrap(
                    "<rect class=\"background\" width=\"50\" height=\"20\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\""),
                    "background rects should get fill=\"none\"");
        }

        @Test
        void injectsFillNoneOnBackgroundRectsWithStyle() {
            String svg = svgWrap(
                    "<rect style=\"stroke: none\" class=\"background\" width=\"50\" height=\"20\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\""),
                    "background rects with style attr should still get fill=\"none\"");
        }

        @Test
        void matchesBackgroundInCompoundClass() {
            String svg = svgWrap(
                    "<rect class=\"label background\" width=\"50\" height=\"20\"></rect>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\""),
                    "class containing 'background' among others should match");
        }
    }

    // ── CSS fill/stroke inlining ──────────────────────────────────────────
    @Nested
    class CssFillStrokeInlining {
        @Test
        void inlinesFillOnRectMatchingNodeRectRule() {
            String css = "#dmtools-mermaid .node rect{fill:#ECECFF;stroke:#9370DB;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect class=\"basic\" width=\"50\" height=\"30\"></rect></g>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("fill=\"#ECECFF\""),
                    "fill from CSS should be inlined as presentation attribute");
            assertTrue(result.contains("stroke=\"#9370DB\""),
                    "stroke from CSS should be inlined as presentation attribute");
        }

        @Test
        void doesNotOverrideExistingFillAttribute() {
            String css = "#dmtools-mermaid .node rect{fill:#ECECFF;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect fill=\"red\" class=\"basic\" width=\"50\" height=\"30\"></rect></g>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("fill=\"red\""), "existing fill should not be replaced");
            assertFalse(result.contains("fill=\"#ECECFF\""));
        }

        @Test
        void handlesClassOnlySelector() {
            String css = ".commit0{fill:#0000EC;stroke:#0000EC;}";
            String svg = svgWrap(css,
                    "<circle class=\"commit0\" r=\"5\"></circle>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("fill=\"#0000EC\""));
            assertTrue(result.contains("stroke=\"#0000EC\""));
        }

        @Test
        void handlesCompoundClassSelector() {
            String css = ".stateGroup .composit{fill:white;}";
            String svg = svgWrap(css,
                    "<g class=\"stateGroup\"><rect class=\"composit\" width=\"10\" height=\"10\"></rect></g>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("fill=\"white\""));
        }

        @Test
        void lastCssRuleWinsForSameProperty() {
            String css = ".marker{fill:#333333;} .marker{fill:none;}";
            String svg = svgWrap(css,
                    "<path class=\"marker\" d=\"M0,0L5,5\"/>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("fill=\"none\""), "later CSS rule should win");
        }

        @Test
        void handlesCommaSeparatedSelectors() {
            String css = ".node rect,.node circle{fill:#ECECFF;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect width=\"10\" height=\"10\"></rect>"
                    + "<circle r=\"5\"></circle></g>");
            String result = renderer.inlineCssFillStroke(svg);
            assertTrue(result.contains("<rect") && result.contains("fill=\"#ECECFF\""));
            // circle should also get the fill
            int circleIdx = result.indexOf("<circle");
            assertTrue(circleIdx > 0);
            String afterCircle = result.substring(circleIdx, result.indexOf(">", circleIdx) + 1);
            assertTrue(afterCircle.contains("fill=\"#ECECFF\""));
        }

        @Test
        void ignoresRulesWithoutFillOrStroke() {
            String css = ".node rect{font-size:12px;rx:5px;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect width=\"10\" height=\"10\"></rect></g>");
            String result = renderer.inlineCssFillStroke(svg);
            assertFalse(result.contains("fill=\""));
        }

        @Test
        void noStyleBlockReturnsUnchanged() {
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<rect width=\"10\" height=\"10\"></rect></svg>";
            String result = renderer.inlineCssFillStroke(svg);
            assertEquals(svg, result);
        }

        @Test
        void ancestorClassCheckPreventsWrongTextFill() {
            // .section-root text{fill:#ffffff} should NOT apply to text outside .section-root
            String css = ".section-root text{fill:#ffffff;} .section-0 text{fill:black;}";
            String svg = svgWrap(css,
                    "<g class=\"section-0\"><text>visible</text></g>"
                    + "<g class=\"section-root\"><text>hidden</text></g>");
            String result = renderer.inlineCssFillStroke(svg);
            // Text inside .section-0 should get fill="black", not #ffffff
            int sec0Idx = result.indexOf("visible");
            int secRootIdx = result.indexOf("hidden");
            String beforeVisible = result.substring(Math.max(0, sec0Idx - 100), sec0Idx);
            String beforeHidden = result.substring(Math.max(0, secRootIdx - 100), secRootIdx);
            assertTrue(beforeVisible.contains("fill=\"black\""),
                    "text inside .section-0 should get fill=black");
            assertTrue(beforeHidden.contains("fill=\"#ffffff\""),
                    "text inside .section-root should get fill=#ffffff");
        }

        @Test
        void doesNotApplyAncestorRuleToUnrelatedElement() {
            // .special rect{fill:red} should NOT apply to rect outside .special
            String css = ".special rect{fill:red;}";
            String svg = svgWrap(css,
                    "<g class=\"normal\"><rect width=\"10\" height=\"10\"></rect></g>"
                    + "<g class=\"special\"><rect width=\"10\" height=\"10\"></rect></g>");
            String result = renderer.inlineCssFillStroke(svg);
            // Only one rect should have fill="red"
            assertEquals(1, countOccurrences(result, "fill=\"red\""),
                    "only rect inside .special should get fill=red");
        }
    }

    // ── HSL color conversion ──────────────────────────────────────────────
    @Nested
    class HslColorConversion {
        @Test
        void convertsHslToHex() {
            String svg = svgWrap(".a{fill:hsl(0, 100%, 50%);}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.replaceHslColors(svg);
            assertTrue(result.contains("#ff0000") || result.contains("#FF0000"),
                    "hsl(0,100%,50%) should become red hex");
        }

        @Test
        void convertsPurpleHslCorrectly() {
            String svg = "fill:hsl(240, 100%, 46%)";
            String result = renderer.replaceHslColors(svg);
            assertFalse(result.contains("hsl("), "HSL should be replaced with hex");
            assertTrue(result.startsWith("fill:#"), "should start with fill:#");
        }

        @Test
        void preservesNonHslColors() {
            String svg = "fill:#ff0000";
            String result = renderer.replaceHslColors(svg);
            assertEquals("fill:#ff0000", result);
        }
    }

    // ── RGBA color conversion ─────────────────────────────────────────────
    @Nested
    class RgbaColorConversion {
        @Test
        void convertsRgbaToHex() {
            String result = renderer.replaceRgbaColors("fill:rgba(232,232,232, 0.8)");
            assertFalse(result.contains("rgba("), "rgba should be replaced with hex");
            assertTrue(result.startsWith("fill:#"), "should produce hex color");
        }

        @Test
        void convertsFullyOpaqueRgba() {
            String result = renderer.replaceRgbaColors("fill:rgba(255,0,0, 1)");
            assertTrue(result.contains("#FF0000"), "fully opaque rgba(255,0,0,1) should be #FF0000");
        }

        @Test
        void convertsFullyTransparentRgba() {
            String result = renderer.replaceRgbaColors("fill:rgba(0,0,0, 0)");
            assertTrue(result.contains("#FFFFFF"), "fully transparent rgba against white should be #FFFFFF");
        }

        @Test
        void preservesNonRgbaColors() {
            String result = renderer.replaceRgbaColors("fill:#ff0000");
            assertEquals("fill:#ff0000", result);
        }
    }

    // ── foreignObject removal ─────────────────────────────────────────────
    @Nested
    class ForeignObjectRemoval {
        @Test
        void removesForeignObjectFromSwitch() {
            String svg = svgWrap(
                    "<switch><foreignObject height=\"50\" width=\"150\"><div>Hello</div></foreignObject>"
                    + "<text>Hello</text></switch>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("foreignObject"), "foreignObject should be removed");
            assertFalse(result.contains("<switch>"), "switch wrapper should be removed");
            assertTrue(result.contains("<text"), "text fallback should remain");
        }
    }

    // ── CSS variable resolution ───────────────────────────────────────────
    @Nested
    class CssVariableResolution {
        @Test
        void resolvesCssVariables() {
            String svg = svgWrap(":root{--bg:#ECECFF;} .node{fill:var(--bg);}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.resolveCssVariables(svg);
            assertTrue(result.contains("#ECECFF"), "var(--bg) should be resolved to #ECECFF");
            assertFalse(result.contains("var(--bg)"));
        }

        @Test
        void usesFallbackWhenVariableUndefined() {
            String svg = svgWrap(".a{fill:var(--undefined, #abcdef);}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.resolveCssVariables(svg);
            assertTrue(result.contains("#abcdef"));
        }

        @Test
        void noVariablesReturnsUnchanged() {
            String svg = svgWrap(".a{fill:red;}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.resolveCssVariables(svg);
            assertEquals(svg, result);
        }
    }

    // ── filter removal ────────────────────────────────────────────────────
    @Nested
    class FilterRemoval {
        @Test
        void removesFilterElements() {
            String svg = svgWrap("<defs><filter id=\"f1\"><feGaussianBlur/></filter></defs>"
                    + "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("<filter"), "filter elements should be removed");
        }

        @Test
        void removesFilterAttributes() {
            String svg = svgWrap("<rect filter=\"url(#f1)\" width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("filter=\"url"), "filter attributes should be removed");
        }

        @Test
        void removesKeyframes() {
            String svg = svgWrap("@keyframes dash{to{stroke-dashoffset:0;}}", "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("@keyframes"), "keyframes should be removed");
        }
    }

    // ── image href normalization ──────────────────────────────────────────
    @Nested
    class ImageHrefNormalization {
        @Test
        void convertsHrefToXlinkHref() {
            String svg = svgWrap("<image href=\"data:image/png;base64,abc\" width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("xlink:href="), "href should become xlink:href");
        }

        @Test
        void removesImageWithoutHref() {
            String svg = svgWrap("<image width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("<image"), "image without href should be removed");
        }
    }

    // ── full pipeline integration ─────────────────────────────────────────
    @Nested
    class FullPipelineIntegration {
        @Test
        void stateNodeRectsGetCorrectFill() {
            String css = "#dmtools-mermaid .node rect{fill:#ECECFF;stroke:#9370DB;stroke-width:1px;}";
            String svg = svgWrap(css,
                    "<g class=\"node statediagram-state\">"
                    + "<rect class=\"basic label-container\" height=\"34\" width=\"45\" ry=\"5\" rx=\"5\" />"
                    + "</g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"#ECECFF\""),
                    "state node rects must get fill from CSS inlining");
            assertTrue(result.contains("stroke=\"#9370DB\""),
                    "state node rects must get stroke from CSS inlining");
        }

        @Test
        void erBackgroundRectsGetFillNone() {
            String svg = svgWrap(
                    "<g class=\"edgeLabel\"><g class=\"label\">"
                    + "<rect class=\"background\" height=\"22\" width=\"49\" />"
                    + "<text>places</text></g></g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\""),
                    "ER background rects must get fill=\"none\"");
        }

        @Test
        void erMarkerPathsGetFillNone() {
            String svg = svgWrap(
                    ".marker{fill:#333;} .marker{fill:none;}",
                    "<defs><marker id=\"dmtools-mermaid_er-oneOrMany\"><path d=\"M0,0L5,5L0,10\"/></marker></defs>"
                    + "<rect width=\"10\" height=\"10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            // Marker paths should get fill="none" — attribute order may vary after DOM serialization
            assertTrue(result.contains("fill=\"none\"") && result.contains("<path"),
                    "marker paths must get fill=\"none\" via injection");
        }

        @Test
        void edgePathsDoNotGetBlackFill() {
            String css = "#dmtools-mermaid .edge-thickness-normal{stroke-width:1px;} "
                    + "#dmtools-mermaid{fill:#333;}";
            String svg = svgWrap(css,
                    "<path class=\"edge-thickness-normal relationshipLine\" "
                    + "style=\"undefined;;;undefined\" d=\"M0,0L10,10\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            // undefined style should be removed
            assertFalse(result.contains("style=\"undefined"),
                    "malformed style should be removed");
            // fill=none should be injected
            assertTrue(result.contains("fill=\"none\""),
                    "edge paths should get fill=\"none\"");
        }

        @Test
        void flowchartNodesPreservedAfterNormalization() {
            String css = "#dmtools-mermaid .node rect{fill:#ECECFF;stroke:#9370DB;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect class=\"basic label-container\" "
                    + "height=\"48\" width=\"94\" />"
                    + "<g class=\"label\"><text>Start</text></g></g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"#ECECFF\""));
            assertTrue(result.contains("Start"), "text content must be preserved");
        }

        @Test
        void hslColorsInCssAreConvertedBeforeInlining() {
            String css = ".commit0{fill:hsl(240, 100%, 50%);stroke:hsl(240, 100%, 50%);}";
            String svg = svgWrap(css, "<circle class=\"commit0\" r=\"5\"></circle>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("hsl("), "HSL should be converted to hex");
            assertTrue(result.contains("fill=\"#"), "fill should be hex color");
        }

        @Test
        void cssVariablesResolvedBeforeInlining() {
            String css = ":root{--node-bg:#ECECFF;} .node rect{fill:var(--node-bg);}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><rect width=\"10\" height=\"10\"></rect></g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"#ECECFF\""),
                    "CSS variable should be resolved and inlined");
        }
    }

    // ── utility ───────────────────────────────────────────────────────────
    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
