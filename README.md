# DMTools Mermaid Renderer

Java renderer library for converting Mermaid diagram text to SVG and PNG.

The production direction is:

1. Mermaid DSL to SVG through GraalJS.
2. SVG to PNG through Apache Batik.
3. Keep renderer-specific dependencies and tests outside `dmtools-core`.

Current implementation bundles Mermaid JS for GraalJS, provides a lightweight DOM/SVG runtime through `linkedom`, and converts SVG output to PNG through Apache Batik.

## Local usage

```bash
npm install
npm run build:engine
./gradlew test
./gradlew runRenderer --args='mermaid_to_svg "flowchart TD; A[Start] --> B[Done]" --output diagram.svg'
./gradlew runRenderer --args='mermaid_to_png "flowchart TD; A[Start] --> B[Done]" --output diagram.png'
```

To generate SVG/PNG compatibility artifacts for the supported fixture set:

```bash
./gradlew test -Dmermaid.compat.outputDir=build/mermaid-compatibility
```
