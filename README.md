# DMTools Mermaid Renderer

Java renderer library for converting Mermaid diagram text to SVG and PNG.

The production direction is:

1. Mermaid DSL to SVG through GraalJS.
2. SVG to PNG through Apache Batik.
3. Keep renderer-specific dependencies and tests outside `dmtools-core`.

Current implementation is a standalone renderer baseline with SVG and PNG outputs plus tests for multiple Mermaid-style diagram types. The next production step is replacing the internal lightweight renderer with the `IstiN/mermaid` headless bundle and a Java-backed DOM/SVG shim.

## Local usage

```bash
./gradlew test
./gradlew runRenderer --args='mermaid_to_svg "flowchart TD; A[Start] --> B[Done]" --output diagram.svg'
./gradlew runRenderer --args='mermaid_to_png "flowchart TD; A[Start] --> B[Done]" --output diagram.png'
```
