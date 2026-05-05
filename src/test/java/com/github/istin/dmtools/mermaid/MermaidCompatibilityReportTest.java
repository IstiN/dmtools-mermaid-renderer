package com.github.istin.dmtools.mermaid;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MermaidCompatibilityReportTest {

    @Test
    void writesCompatibilityReportForRequestedDiagramTypes() throws Exception {
        Path outputDir = Path.of(System.getProperty(
                "mermaid.compat.outputDir",
                "build/mermaid-compatibility"
        ));
        Files.createDirectories(outputDir);

        MermaidRenderer renderer = new MermaidRenderer();
        StringBuilder report = new StringBuilder("diagram,svg,png,error\n");
        int svgRendered = 0;

        for (MermaidDiagramFixtures.Fixture fixture : MermaidDiagramFixtures.all()) {
            Path svg = outputDir.resolve(fixture.name() + ".svg");
            Path png = outputDir.resolve(fixture.name() + ".png");
            String svgStatus = "FAIL";
            String pngStatus = "FAIL";
            String error = "";
            try {
                renderer.renderToSvgFile(fixture.definition(), svg);
                svgStatus = "PASS";
                svgRendered++;
            } catch (Exception e) {
                error = e.getMessage();
            }
            if ("PASS".equals(svgStatus)) {
                try {
                    renderer.renderToPng(fixture.definition(), png);
                    pngStatus = "PASS";
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
            report.append(fixture.name())
                    .append(',')
                    .append(svgStatus)
                    .append(',')
                    .append(pngStatus)
                    .append(",\"")
                    .append(error == null ? "" : error.replace("\"", "\"\"").replace("\n", " "))
                    .append("\"\n");
        }

        Files.writeString(outputDir.resolve("compatibility-report.csv"), report.toString(), StandardCharsets.UTF_8);

        assertTrue(svgRendered > 0, "At least one diagram type should render so report artifacts are meaningful");
    }
}
