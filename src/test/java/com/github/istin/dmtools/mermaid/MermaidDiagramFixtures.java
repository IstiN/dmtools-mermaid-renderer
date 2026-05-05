package com.github.istin.dmtools.mermaid;

import java.util.List;

final class MermaidDiagramFixtures {

    private MermaidDiagramFixtures() {
    }

    static List<Fixture> all() {
        return List.of(
                fixture("flowchart", """
                        flowchart TD
                          A[Start] --> B{Is blocked?}
                          B -->|Yes| C[Check blockers]
                          C --> D{All done?}
                          D -->|No| E[Keep blocked]
                          D -->|Yes| F[Move to Backlog]
                          B -->|No| G[Work normally]
                        """),
                fixture("class", """
                        classDiagram
                          class MermaidRenderer
                          MermaidRenderer : +renderToSvg(String)
                          MermaidRenderer : +renderToPng(String, Path)
                          class GraalBridge
                          GraalBridge : +eval(String)
                          class SvgTranscoder
                          SvgTranscoder : +toPng(String, Path)
                          MermaidRenderer --> GraalBridge : uses
                          MermaidRenderer --> SvgTranscoder : converts
                        """),
                fixture("sequence", """
                        sequenceDiagram
                          participant User
                          participant DMTools
                          participant Renderer
                          User->>DMTools: mermaid_to_png
                          DMTools->>Renderer: renderToPng()
                          Renderer-->>DMTools: png path
                          DMTools-->>User: output path
                        """),
                fixture("entity-relationship", """
                        erDiagram
                          CUSTOMER ||--o{ ORDER : places
                          ORDER ||--|{ LINE_ITEM : contains
                          PRODUCT ||--o{ LINE_ITEM : includes
                          CUSTOMER {
                            string id
                            string name
                            string email
                          }
                          ORDER {
                            string id
                            date createdAt
                            string status
                          }
                          LINE_ITEM {
                            string id
                            int quantity
                          }
                          PRODUCT {
                            string sku
                            string title
                          }
                        """),
                fixture("state", """
                        stateDiagram-v2
                          [*] --> Still
                          Still --> Moving
                          Moving --> Still
                          Moving --> Crash
                          Crash --> [*]
                        """),
                fixture("mindmap", """
                        mindmap
                          root((DMTools))
                            Renderer
                              SVG
                              PNG
                            Integrations
                              Jira
                              GitHub
                            CLI
                              mermaid_to_svg
                              mermaid_to_png
                        """),
                fixture("architecture", """
                        architecture-beta
                          group api(cloud)[DMTools]
                          service cli(server)[CLI] in api
                          service renderer(server)[Renderer] in api
                          service jira(database)[Jira] in api
                          cli:R --> L:renderer
                          renderer:R --> L:jira
                        """),
                fixture("block", """
                        block
                          columns 3
                          A["Input Mermaid"]
                          B["Render SVG"]
                          C["Convert PNG"]
                          A --> B
                          B --> C
                        """),
                fixture("c4", """
                        C4Context
                          title DMTools Mermaid Renderer
                          Person(user, "User")
                          System(dmtools, "DMTools CLI")
                          System(renderer, "Mermaid Renderer")
                          Rel(user, dmtools, "Runs")
                          Rel(dmtools, renderer, "Delegates rendering")
                        """),
                fixture("gantt", """
                        gantt
                          title Renderer Production Plan
                          dateFormat  YYYY-MM-DD
                          section Renderer
                          DOM shim           :a1, 2026-05-01, 7d
                          Mermaid bundle     :a2, after a1, 5d
                          Visual validation  :a3, after a2, 4d
                        """),
                fixture("git", """
                        gitGraph
                          commit id: "init"
                          commit id: "renderer"
                          branch renderer
                          checkout renderer
                          commit id: "svg"
                          commit id: "png"
                          checkout main
                          commit id: "docs"
                          merge renderer
                          commit id: "release"
                        """),
                fixture("ishikawa", """
                        ishikawa-beta
                          Rendering quality
                            DOM
                              Missing layout
                              Text metrics
                            SVG
                              CSS support
                              ViewBox
                            PNG
                              Batik
                              Transparency
                            Tests
                              Fixtures
                              Visual report
                        """),
                fixture("kanban", """
                        kanban
                          backlog[Backlog]
                            dom[DOM shim]
                            bundle[Mermaid bundle]
                          progress[In Progress]
                            cli[CLI adapter]
                          done[Done]
                            repo[Renderer repo]
                        """),
                fixture("packet", """
                        packet-beta
                          title TCP Packet
                          0-15: "Source Port"
                          16-31: "Destination Port"
                          32-63: "Sequence Number"
                          64-95: "Acknowledgment Number"
                        """),
                fixture("pie", """
                        pie title Renderer work split
                          "DOM shim" : 45
                          "Mermaid bundle" : 30
                          "Tests" : 25
                        """),
                fixture("quadrant", """
                        quadrantChart
                          title Renderer Options
                          x-axis Low effort --> High effort
                          y-axis Low quality --> High quality
                          quadrant-1 Production
                          quadrant-2 Risky
                          quadrant-3 Avoid
                          quadrant-4 Quick win
                          Browser: [0.8, 0.9]
                          Toy renderer: [0.2, 0.3]
                          Graal DOM shim: [0.7, 0.85]
                        """),
                fixture("radar", """
                        radar-beta
                          axis Quality, Coverage, Speed, Maintenance, Portability
                          curve MermaidEngine["Mermaid Engine"]{90,85,70,80,95}
                          curve ToyRenderer["Toy Renderer"]{35,20,95,30,90}
                        """),
                fixture("requirement", """
                        requirementDiagram
                          requirement renderer {
                            id: 1
                            text: Render Mermaid to PNG
                            risk: medium
                            verifymethod: test
                          }
                          element cli {
                            type: interface
                          }
                          cli - satisfies -> renderer
                        """),
                fixture("sankey", """
                        sankey-beta
                          Mermaid,SVG,100
                          SVG,PNG,80
                          SVG,Diagnostics,20
                        """),
                fixture("timeline", """
                        timeline
                          title Mermaid Renderer
                          POC : Toy renderer
                          Extraction : Standalone Java repo
                          Production : Real Mermaid bundle
                          Validation : Visual samples
                        """),
                fixture("treeview", """
                        treeView-beta
                            "dmtools/"
                                "mermaid_to_svg"
                                "mermaid_to_png"
                                "renderer/"
                                    "graaljs"
                                    "batik"
                        """),
                fixture("treemap", """
                        treemap-beta
                          "Renderer"
                            "DOM shim": 40
                            "Mermaid bundle": 35
                            "Batik": 15
                            "Tests": 10
                        """),
                fixture("user-journey", """
                        journey
                          title Developer renders diagram
                          section CLI
                            Writes Mermaid text: 5: Developer
                            Runs dmtools mermaid_to_png: 4: Developer
                          section Renderer
                            Generates SVG: 5: Renderer
                            Converts PNG: 5: Renderer
                        """),
                fixture("venn", """
                        venn-beta
                          title Renderer Concerns
                          set Quality:40
                          set Portability:35
                          set Speed:25
                          union Quality,Portability:15
                          union Quality,Speed:10
                        """),
                fixture("wardley", """
                        wardley-beta
                          title Renderer Strategy
                          anchor User [0.95, 0.65]
                          component DMTools CLI [0.75, 0.55]
                          component Mermaid Renderer [0.55, 0.45]
                          component DOM Shim [0.35, 0.35]
                          User->DMTools CLI
                          DMTools CLI->Mermaid Renderer
                          Mermaid Renderer->DOM Shim
                        """),
                fixture("xy", """
                        xychart-beta
                          title "Renderer quality over iterations"
                          x-axis [POC, Extracted, MermaidEngine, Validated]
                          y-axis "Quality" 0 --> 100
                          line [20, 45, 85, 95]
                        """),
                fixture("zenuml", """
                        zenuml
                          title Renderer call
                          User->DMTools: mermaid_to_png
                          DMTools->Renderer: renderToPng()
                          Renderer-->DMTools: path
                        """)
        );
    }

    private static Fixture fixture(String name, String definition) {
        return new Fixture(name, definition.stripIndent().trim());
    }

    record Fixture(String name, String definition) {
    }
}
