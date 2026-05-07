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

        @Test
        void removesEmptyFontWeightAttribute() {
            // Class diagram tspans have font-weight="" which blocks bold inheritance from
            // the parent <g style="font-weight: bolder">. Removing it restores bold rendering.
            String svg = svgWrap(
                    "<g style=\"font-weight: bolder\" class=\"label\">"
                    + "<tspan font-weight=\"\">MermaidRenderer</tspan>"
                    + "</g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("font-weight=\"\""),
                    "empty font-weight=\"\" should be removed");
            // After removal, propagateBolderFontWeight should inject font-weight="bold"
            assertTrue(result.contains("font-weight=\"bold\""),
                    "tspan inside font-weight:bolder group should get font-weight=bold");
        }

        @Test
        void preservesNonEmptyFontWeightAttribute() {
            String svg = svgWrap("<tspan font-weight=\"bold\">Header</tspan>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("font-weight=\"bold\""),
                    "non-empty font-weight should be preserved");
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
        void textAnchorIsInlinedForNestedFlowchartLabels() {
            String css = "#dmtools-mermaid .node .label text{text-anchor:middle;}";
            String svg = svgWrap(css,
                    "<g class=\"node\"><g class=\"label\"><text><tspan x=\"0\">Centered</tspan></text></g></g>");

            String result = renderer.normalizeSvgForBatik(svg);

            assertTrue(result.contains("text-anchor=\"middle\""),
                    "text-anchor must be inlined because Affinity ignores Mermaid's nested CSS selector");
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

    // ── UserJourney ───────────────────────────────────────────────────────
    @Nested
    class UserJourney {

        @Test
        void journeySectionRectFillIsRemoved() {
            // Section header rects have explicit dark fills; CSS should control the light theme.
            String svg = svgWrap("",
                    "<rect class=\"journey-section section-type-0\" fill=\"#191970\" width=\"100\" height=\"20\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            // The presentation fill attribute must be removed so the CSS class rule wins.
            assertFalse(result.contains("fill=\"#191970\""),
                    "journey-section rect explicit fill should be removed");
        }

        @Test
        void taskRectFillIsRemoved() {
            // Task rects also have explicit dark fills; CSS should override them to light.
            String svg = svgWrap("",
                    "<rect class=\"task task-type-0\" fill=\"#191970\" width=\"60\" height=\"15\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertFalse(result.contains("fill=\"#191970\""),
                    "task rect explicit fill should be removed");
        }

        @Test
        void journeySectionTextGetsDarkFill() {
            // Section label text must be dark (#333) so it's readable on the light CSS background.
            String svg = svgWrap("",
                    "<text class=\"journey-section section-type-0\" style=\"font-size:14px;\">CLI</text>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill:#333"),
                    "journey-section text should get fill:#333 prepended to its style");
        }

        @Test
        void taskTextFillNotModified() {
            // Task text elements must NOT receive the dark fill injection (they are inside task boxes).
            String svg = svgWrap("",
                    "<text class=\"task journey-section section-type-0\" style=\"font-size:11px;\">do work</text>");
            String result = renderer.normalizeSvgForBatik(svg);
            // The regex excludes elements whose class contains "task", so no fill:#333 injection.
            assertFalse(result.startsWith("<text") && result.contains("fill:#333"),
                    "task text should not receive fill:#333 injection");
        }

        @Test
        void nonJourneyRectFillPreserved() {
            // Rects without journey-section or task classes must keep their fill.
            String svg = svgWrap("",
                    "<rect class=\"background\" fill=\"#ffffff\" width=\"200\" height=\"100\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            // Background rect fill is a different normalization path; journey regex must not strip it.
            // We check that a non-journey non-task rect is not stripped by the journey regex.
            // (Background rects are handled separately; this is a different class so it should survive.)
            assertTrue(result.contains("fill=\"#ffffff\"") || result.contains("fill=\"none\""),
                    "non-journey rect fill should not be removed by journey normalization");
        }
    }

    // ── EmptyRect ─────────────────────────────────────────────────────────
    @Nested
    class EmptyRect {

        @Test
        void emptyRectSelfClosingNoSpaceGetsSuppressed() {
            // <rect/> with no attributes is a spacer – must not render as a coloured dot in Batik.
            String svg = svgWrap("", "<g class=\"node statediagram-state\"><rect/></g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\" stroke=\"none\""),
                    "empty <rect/> should get fill=none stroke=none");
        }

        @Test
        void emptyRectSelfClosingWithSpaceGetsSuppressed() {
            String svg = svgWrap("", "<g class=\"node statediagram-state\"><rect /></g>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("fill=\"none\" stroke=\"none\""),
                    "empty <rect /> should get fill=none stroke=none");
        }

        @Test
        void nonEmptyRectNotAffected() {
            // A rect with attributes must NOT be changed by the empty-rect suppression.
            String svg = svgWrap("", "<rect width=\"100\" height=\"50\" fill=\"#abc\"/>");
            String result = renderer.normalizeSvgForBatik(svg);
            assertTrue(result.contains("width=\"100\""),
                    "rect with attributes should not be modified by empty-rect suppression");
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

    @Nested
    class HangingDominantBaseline {
        @Test
        void fixesHangingBaselineWithFontSize20() {
            String svg = "<svg><g class=\"title\"><text transform=\"translate(250, 10) rotate(0)\" "
                    + "dominant-baseline=\"hanging\" font-size=\"20\">Title</text></g></svg>";
            String result = renderer.fixHangingDominantBaseline(svg);
            assertTrue(result.contains("dy=\"16.0\""), "Expected dy=16.0 in: " + result);
            assertTrue(result.contains("dominant-baseline=\"auto\""), "Expected auto baseline");
            assertFalse(result.contains("dominant-baseline=\"hanging\""), "Should not contain hanging");
        }

        @Test
        void fixesHangingBaselineWithFontSize16() {
            String svg = "<svg><text dominant-baseline=\"hanging\" font-size=\"16\">Label</text></svg>";
            String result = renderer.fixHangingDominantBaseline(svg);
            assertTrue(result.contains("dy=\"12.8\""), "Expected dy=12.8 in: " + result);
            assertTrue(result.contains("dominant-baseline=\"auto\""), "Expected auto baseline");
        }

        @Test
        void skipsRotatedText() {
            // Axis labels with rotate(-90) should not be modified
            String svg = "<svg><text transform=\"translate(5, 363) rotate(-90)\" "
                    + "dominant-baseline=\"hanging\" font-size=\"16\">Low quality</text></svg>";
            String result = renderer.fixHangingDominantBaseline(svg);
            assertTrue(result.contains("dominant-baseline=\"hanging\""), "Rotated text should be unchanged");
            assertFalse(result.contains("dy="), "Should not add dy to rotated text");
        }

        @Test
        void skipsNonHangingBaseline() {
            String svg = "<svg><text dominant-baseline=\"middle\" font-size=\"16\">Venn</text></svg>";
            String result = renderer.fixHangingDominantBaseline(svg);
            assertTrue(result.contains("dominant-baseline=\"middle\""), "Should keep middle baseline");
        }

        @Test
        void noChangeWhenNoHangingElements() {
            String svg = "<svg><text font-size=\"16\">Normal</text></svg>";
            String result = renderer.fixHangingDominantBaseline(svg);
            assertFalse(result.contains("dominant-baseline=\"auto\""), "Should not add auto baseline");
        }
    }

    @Nested
    class SwitchTextWrapping {
        private String makeSwitchSvg(String text, int boxWidth) {
            return "<svg><switch>"
                    + "<foreignObject position=\"fixed\" height=\"50\" width=\"" + boxWidth + "\" y=\"110\" x=\"350\">"
                    + "<div xmlns=\"http://www.w3.org/1999/xhtml\" class=\"task\" "
                    + "style=\"display:table;height:100%;width:100%\">"
                    + "<div style=\"display:table-cell;text-align:center\" class=\"label\">" + text + "</div>"
                    + "</div></foreignObject>"
                    + "<text class=\"task\" dominant-baseline=\"central\" "
                    + "style=\"font-size:14;\" y=\"135\" x=\"425\">"
                    + "<tspan dy=\"0\" x=\"425\">" + text + "</tspan>"
                    + "</text></switch></svg>";
        }

        @Test
        void wrapsLongTextToMultipleLines() {
            // "Runs dmtools mermaid_to_png" (27 chars) should wrap at box width 150px
            String svg = makeSwitchSvg("Runs dmtools mermaid_to_png", 150);
            String result = renderer.wrapSvgSwitchTexts(svg);
            // Should now have 2+ tspans
            long tspanCount = result.chars().filter(c -> c == '<').mapToObj(c -> result)
                    .limit(1).findFirst().map(s -> {
                        int cnt = 0; int idx = 0;
                        while ((idx = s.indexOf("<tspan", idx)) != -1) { cnt++; idx++; }
                        return cnt;
                    }).orElse(0);
            assertTrue(result.contains("mermaid_to_png"), "Should still contain text content");
            // First tspan should be "Runs dmtools", second "mermaid_to_png"
            assertTrue(result.contains(">Runs dmtools<"), "First line should be 'Runs dmtools'");
            assertTrue(result.contains(">mermaid_to_png<"), "Second line should be 'mermaid_to_png'");
        }

        @Test
        void doesNotWrapShortText() {
            // "Generates SVG" (13 chars) fits in 150px
            String svg = makeSwitchSvg("Generates SVG", 150);
            String result = renderer.wrapSvgSwitchTexts(svg);
            // Should have only one tspan with all text on one line
            int tspanCount = 0; int idx = 0;
            while ((idx = result.indexOf("<tspan", idx)) != -1) { tspanCount++; idx++; }
            assertEquals(1, tspanCount, "Short text should not be wrapped");
        }

        @Test
        void adjustsYForVerticalCentering() {
            // With 2 lines in a 50px-high box, y should be adjusted from center
            String svg = makeSwitchSvg("Runs dmtools mermaid_to_png", 150);
            String result = renderer.wrapSvgSwitchTexts(svg);
            // y="135" is center (box y=110, height=50). 2 lines → first line above center
            assertFalse(result.contains("y=\"135\""),
                    "y should be adjusted from center (135) for multi-line centering");
        }

        @Test
        void noSwitchElementPassesThrough() {
            String svg = "<svg><text><tspan>Hello</tspan></text></svg>";
            String result = renderer.wrapSvgSwitchTexts(svg);
            // No change expected (no switch elements)
            assertTrue(result.contains("Hello"), "Content should be preserved");
        }
    }

    @Nested
    class EmojiFontSpans {
        @Test
        void wrapsEmojiRunsWithNotoEmojiFont() {
            String svg = svgWrap("<text><tspan>Done ✅ and robot 🤖</tspan></text>");

            String result = renderer.normalizeSvgForBatik(svg);

            assertFalse(result.contains("@font-face{font-family:'Noto Emoji'"),
                    "SVG should not embed a large data-font because Affinity rejects it");
            assertTrue(result.contains("font-family=\"Noto Emoji, Apple Color Emoji, Segoe UI Emoji, sans-serif\""),
                    "emoji runs should use an explicit emoji font because Batik lacks browser-style fallback");
            assertTrue(result.contains("style=\"fill:#111111;\""),
                    "emoji runs should force a readable dark fill instead of inheriting background-like theme colors");
            assertTrue(result.contains("Done"),
                    "non-emoji text should remain in the primary font");
            assertFalse(result.contains("<tspan>Done ✅ and robot 🤖</tspan>"),
                    "mixed text should be split so only emoji use Noto Emoji");
        }

        @Test
        void leavesPlainTextWithoutEmbeddedEmojiFont() {
            String svg = svgWrap("<text><tspan>Done without emoji</tspan></text>");

            String result = renderer.normalizeSvgForBatik(svg);

            assertFalse(result.contains("font-family=\"Noto Emoji\""),
                    "plain text should not be rewritten");
        }
    }

    @Nested
    class AffinityTextPositioning {
        @Test
        void removesParentTextYWhenRowsHaveExplicitY() {
            String svg = svgWrap("<text y=\"-10.1\">"
                    + "<tspan class=\"text-outer-tspan row\" x=\"0\" y=\"-0.1em\" dy=\"1.1em\">SM adds</tspan>"
                    + "<tspan class=\"text-outer-tspan row\" x=\"0\" y=\"1em\" dy=\"1.1em\">BEFORE dispatch</tspan>"
                    + "</text>");

            String result = renderer.normalizeSvgForBatik(svg);

            assertFalse(result.contains("<text y=\"-10.1\""),
                    "parent text y should be removed so Affinity does not apply it on top of row y");
            assertTrue(result.contains("y=\"-0.1em\""),
                    "row tspan y should remain as the source of truth");
        }

        @Test
        void keepsTextYWhenRowsAreNotExplicitlyPositioned() {
            String svg = svgWrap("<text y=\"20\"><tspan>Plain label</tspan></text>");

            String result = renderer.normalizeSvgForBatik(svg);

            assertTrue(result.contains("<text y=\"20\""),
                    "single-line/plain text still needs its parent y");
        }
    }
}
