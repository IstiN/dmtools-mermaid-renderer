/**
 * Renders all Mermaid diagram fixtures using Playwright/Chromium (ground truth).
 * Uses IDENTICAL fixture definitions as MermaidDiagramFixtures.java for 1:1 comparison.
 * Output: --outDir/<name>.png
 *
 * Usage: node scripts/playwright-render.mjs --outDir /path/to/dir
 */

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'fs';
import { resolve } from 'path';

// === IDENTICAL to MermaidDiagramFixtures.java ===
const FIXTURES = [
  { name: 'flowchart', definition: `flowchart TD
  A[Start] --> B{Is blocked?}
  B -->|Yes| C[Check blockers]
  C --> D{All done?}
  D -->|No| E[Keep blocked]
  D -->|Yes| F[Move to Backlog]
  B -->|No| G[Work normally]` },

  { name: 'class', definition: `classDiagram
  class MermaidRenderer
  MermaidRenderer : +renderToSvg(String)
  MermaidRenderer : +renderToPng(String, Path)
  class GraalBridge
  GraalBridge : +eval(String)
  class SvgTranscoder
  SvgTranscoder : +toPng(String, Path)
  MermaidRenderer --> GraalBridge : uses
  MermaidRenderer --> SvgTranscoder : converts` },

  { name: 'sequence', definition: `sequenceDiagram
  participant User
  participant DMTools
  participant Renderer
  User->>DMTools: mermaid_to_png
  DMTools->>Renderer: renderToPng()
  Renderer-->>DMTools: png path
  DMTools-->>User: output path` },

  { name: 'entity-relationship', definition: `erDiagram
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
  }` },

  { name: 'state', definition: `stateDiagram-v2
  [*] --> Still
  Still --> Moving
  Moving --> Still
  Moving --> Crash
  Crash --> [*]` },

  { name: 'mindmap', definition: `mindmap
  root((DMTools))
    Renderer
      SVG
      PNG
    Integrations
      Jira
      GitHub
    CLI
      mermaid_to_svg
      mermaid_to_png` },

  { name: 'architecture', definition: `architecture-beta
  group api(cloud)[DMTools]
  service cli(server)[CLI] in api
  service renderer(server)[Renderer] in api
  service jira(database)[Jira] in api
  cli:R --> L:renderer
  renderer:R --> L:jira` },

  { name: 'block', definition: `block
  columns 3
  A["Input Mermaid"]
  B["Render SVG"]
  C["Convert PNG"]
  A --> B
  B --> C` },

  { name: 'c4', definition: `C4Context
  title DMTools Mermaid Renderer
  Person(user, "User")
  System(dmtools, "DMTools CLI")
  System(renderer, "Mermaid Renderer")
  Rel(user, dmtools, "Runs")
  Rel(dmtools, renderer, "Delegates rendering")` },

  { name: 'gantt', definition: `gantt
  title Renderer Production Plan
  dateFormat  YYYY-MM-DD
  section Renderer
  DOM shim           :a1, 2026-05-01, 7d
  Mermaid bundle     :a2, after a1, 5d
  Visual validation  :a3, after a2, 4d` },

  { name: 'git', definition: `gitGraph
  commit id: "init"
  commit id: "renderer"
  branch renderer
  checkout renderer
  commit id: "svg"
  commit id: "png"
  checkout main
  commit id: "docs"
  merge renderer
  commit id: "release"` },

  { name: 'ishikawa', definition: `ishikawa-beta
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
      Visual report` },

  { name: 'kanban', definition: `kanban
  backlog[Backlog]
    dom[DOM shim]
    bundle[Mermaid bundle]
  progress[In Progress]
    cli[CLI adapter]
  done[Done]
    repo[Renderer repo]` },

  { name: 'packet', definition: `packet-beta
  title TCP Packet
  0-15: "Source Port"
  16-31: "Destination Port"
  32-63: "Sequence Number"
  64-95: "Acknowledgment Number"` },

  { name: 'pie', definition: `pie title Renderer work split
  "DOM shim" : 45
  "Mermaid bundle" : 30
  "Tests" : 25` },

  { name: 'quadrant', definition: `quadrantChart
  title Renderer Options
  x-axis Low effort --> High effort
  y-axis Low quality --> High quality
  quadrant-1 Production
  quadrant-2 Risky
  quadrant-3 Avoid
  quadrant-4 Quick win
  Browser: [0.8, 0.9]
  Toy renderer: [0.2, 0.3]
  Graal DOM shim: [0.7, 0.85]` },

  { name: 'radar', definition: `radar-beta
  axis Quality, Coverage, Speed, Maintenance, Portability
  curve MermaidEngine["Mermaid Engine"]{90,85,70,80,95}
  curve ToyRenderer["Toy Renderer"]{35,20,95,30,90}` },

  { name: 'requirement', definition: `requirementDiagram
  requirement renderer {
    id: 1
    text: Render Mermaid to PNG
    risk: medium
    verifymethod: test
  }
  element cli {
    type: interface
  }
  cli - satisfies -> renderer` },

  { name: 'sankey', definition: `sankey-beta
Mermaid,SVG,100
SVG,PNG,80
SVG,Diagnostics,20` },

  { name: 'timeline', definition: `timeline
  title Mermaid Renderer
  POC : Toy renderer
  Extraction : Standalone Java repo
  Production : Real Mermaid bundle
  Validation : Visual samples` },

  { name: 'treeview', definition: `treeView-beta
    "dmtools/"
        "mermaid_to_svg"
        "mermaid_to_png"
        "renderer/"
            "graaljs"
            "batik"` },

  { name: 'treemap', definition: `treemap-beta
  "Renderer"
    "DOM shim": 40
    "Mermaid bundle": 35
    "Batik": 15
    "Tests": 10` },

  { name: 'user-journey', definition: `journey
  title Developer renders diagram
  section CLI
    Writes Mermaid text: 5: Developer
    Runs dmtools mermaid_to_png: 4: Developer
  section Renderer
    Generates SVG: 5: Renderer
    Converts PNG: 5: Renderer` },

  { name: 'venn', definition: `venn-beta
  title Renderer Concerns
  set Quality:40
  set Portability:35
  set Speed:25
  union Quality,Portability:15
  union Quality,Speed:10` },

  { name: 'wardley', definition: `wardley-beta
  title Renderer Strategy
  anchor User [0.95, 0.65]
  component DMTools CLI [0.75, 0.55]
  component Mermaid Renderer [0.55, 0.45]
  component DOM Shim [0.35, 0.35]
  User->DMTools CLI
  DMTools CLI->Mermaid Renderer
  Mermaid Renderer->DOM Shim` },

  { name: 'xy', definition: `xychart-beta
  title "Renderer quality over iterations"
  x-axis [POC, Extracted, MermaidEngine, Validated]
  y-axis "Quality" 0 --> 100
  line [20, 45, 85, 95]` },
];

async function renderWithPlaywright(outDir) {
  mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1400, height: 900 });

  await page.setContent(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { margin: 0; padding: 20px; background: white; font-family: sans-serif; }
    #container { display: inline-block; background: white; }
  </style>
</head>
<body>
  <div id="container"><div id="diagram"></div></div>
  <script type="module">
    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      htmlLabels: false,
    });
    window.renderMermaid = async (definition) => {
      document.getElementById('diagram').innerHTML = '';
      const { svg } = await mermaid.render('mermaid-diagram', definition);
      document.getElementById('diagram').innerHTML = svg;
      await new Promise(r => setTimeout(r, 200));
    };
    window.mermaidReady = true;
  </script>
</body>
</html>`, { waitUntil: 'networkidle' });

  await page.waitForFunction(() => window.mermaidReady === true, { timeout: 30000 });

  for (const fixture of FIXTURES) {
    const outPath = resolve(outDir, `${fixture.name}.png`);
    try {
      await page.evaluate((def) => window.renderMermaid(def), fixture.definition);
      await page.waitForTimeout(300);
      const container = await page.$('#container');
      if (container) {
        await container.screenshot({ path: outPath, omitBackground: false });
        // Also save the SVG for analysis
        const svgContent = await page.evaluate(() => {
          const svgEl = document.querySelector('#diagram svg');
          return svgEl ? svgEl.outerHTML : null;
        });
        if (svgContent) {
          const { writeFileSync } = await import('fs');
          writeFileSync(outPath.replace('.png', '.svg'), svgContent, 'utf8');
        }
        console.log(`✓ ${fixture.name}`);
      } else {
        console.error(`✗ ${fixture.name}: container not found`);
      }
    } catch (err) {
      console.error(`✗ ${fixture.name}: ${err.message.split('\n')[0]}`);
      writeFileSync(outPath.replace('.png', '-error.txt'), err.message);
    }
  }

  await browser.close();
  console.log(`\nDone → ${outDir}`);
}

const args = process.argv.slice(2);
const outDirIdx = args.indexOf('--outDir');
const outDir = outDirIdx >= 0 ? args[outDirIdx + 1] : './playwright-output';

renderWithPlaywright(outDir).catch(err => { console.error(err); process.exit(1); });
