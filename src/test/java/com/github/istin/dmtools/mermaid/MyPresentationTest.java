package com.github.istin.dmtools.mermaid;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ✏️  Personal sandbox — edit DIAGRAM below, then run:
 *
 *   ./gradlew test --tests "*MyPresentationTest*"
 *
 * Output files land in build/my-presentation/
 *   → diagram.svg
 *   → diagram.png
 */
class MyPresentationTest {

    // ─────────────────────────────────────────────────────────────
    // PUT YOUR MERMAID DIAGRAM HERE ↓
    // ─────────────────────────────────────────────────────────────
    private static final String DIAGRAM = """
            flowchart TB
                  classDef humanInput fill:#fef3c7,stroke:#f59e0b,color:#92400e,font-weight:bold
                  classDef action fill:#f0f9ff,stroke:#0ea5e9,color:#0c4a6e
                  classDef status fill:#f0fdf4,stroke:#16a34a,color:#14532d
    
                  %% ─── INTAKE ──────────────────────────────────────────────────────────
                  subgraph INTAKE["📥 Intake — intake.json (manual or auto trigger)"]
                      direction TB
                      act_intake_trig["Triggered manually or automatically<br/>📥 reads input/request.md + existing_epics.json<br/>🤖 decomposes into structured tickets<br/>📋 writes outputs/stories.json"]:::action
                      dec_intake{Type?}
                      act_intake_trig --> dec_intake
                      in_feat["Creates Epics + Stories<br/>📎 links to source ticket<br/>source → In Progress"]:::action
                      in_bug_out["Creates Bug ticket<br/>→ Ready For Dev<br/>📎 links to source ticket"]:::action
                      dec_intake -->|"new feature"| in_feat
                      dec_intake -->|"bug report"| in_bug_out
                  end
    
                  %% ─── STORY PIPELINE ──────────────────────────────────────────────────
                  subgraph STORY["📖 Story Pipeline"]
                      direction TB
                      s_bl([Backlog]):::status
                      s_por["po_refinement.json · AI PO<br/>🤖 reads questions · writes answers<br/>on subtask tickets · closes subtasks"]:::action
                      s_ba([BA Analysis]):::status
                      s_sa([Solution Architecture]):::status
                      s_rfd([Ready For Dev]):::status
                      s_inr(["In Review · GitHub PR open"]):::status
                      s_rwk([In Rework]):::status
                      s_mrg([Merged]):::status
                      s_int([In Testing]):::status
                      s_don([Done ✅]):::status
                      s_blocked([Blocked ⛔]):::status
    
                      s_bl -->|"story_questions<br/>📋 Q subtasks + label q"| s_por
                      s_por -->|"story_ba_check<br/>✅ all subtasks Done?"| s_ba
                      s_ba -->|"story_acceptance_criterias<br/>📝 AC to Story field"| s_sa
                      s_sa -->|"story_solution<br/>📐 Solution Design + diagrams"| s_rfd
                      s_rfd -->|"story_development<br/>💻 code · 🔀 opens GitHub PR"| s_inr
                      s_inr -->|"pr_review · 💬 existing PR<br/>✅ pr_approved"| s_mrg
                      s_inr -->|"❌"| s_rwk
                      s_rwk -->|"pr_rework · 📝 existing PR"| s_inr
                      s_mrg -->|"test_cases_generator<br/>📋 Test Case tickets · Story→InTesting"| s_int
                      s_int -->|"story_done_check<br/>✅ all TCs Passed?"| s_don
                  end
    
                  %% ─── BUG PIPELINE ────────────────────────────────────────────────────
                  subgraph BUG["🐛 Bug Pipeline"]
                      direction TB
                      b_bl([Backlog]):::status
                      b_rfd([Ready For Dev]):::status
                      b_inr(["In Review · GitHub PR open"]):::status
                      b_rwk([In Rework]):::status
                      b_mrg([Merged]):::status
                      b_rft([Ready For Testing]):::status
                      b_don([Done ✅]):::status
                      b_blocked([Blocked ⛔]):::status
    
                      b_bl --> b_rfd
                      b_rfd -->|"bug_development<br/>💻 fix · 🔀 opens GitHub PR"| b_inr
                      b_inr -->|"pr_review · 💬 existing PR<br/>✅ pr_approved"| b_mrg
                      b_inr -->|"❌"| b_rwk
                      b_rwk -->|"pr_rework · 📝 existing PR"| b_inr
                      b_mrg -->|"bug_merged<br/>🤖 Gemini: RCA + Solution to fields"| b_rft
                      b_rft -->|"bug_test_cases_generator<br/>📋 Test Case tickets · Bug→Done"| b_don
                  end
    
                  %% ─── TEST CASE PIPELINE ──────────────────────────────────────────────
                  subgraph TC["🧪 Test Case Pipeline"]
                      direction TB
                      tc_bl([Backlog]):::status
                      tc_dev(["In Development · GitHub PR open"]):::status
                      tc_inr([In Review]):::status
                      tc_rwk([In Rework]):::status
                      tc_pas([Passed ✅]):::status
                      tc_fai([Failed ❌]):::status
                      tc_btf([Bug To Fix]):::status
                      tc_blocked([Blocked ⛔]):::status
    
                      tc_bl -->|"test_case_automation<br/>💻 test code · 🔀 opens GitHub PR"| tc_dev
                      tc_dev -->|"pr_test_automation_review<br/>💬 existing PR"| tc_inr
                      tc_inr -->|"🔄 rework"| tc_rwk
                      tc_rwk -->|"pr_test_automation_rework<br/>📝 existing PR"| tc_dev
                      tc_inr -->|"✅ Passed + 🔀 merge"| tc_pas
                      tc_inr -->|"❌ Failed + 🔀 merge"| tc_fai
                      tc_fai -->|"bug_creation<br/>🤖 create / link"| tc_btf
                      tc_fai -->|"bug_creation<br/>none — unclear"| tc_blocked
                      tc_btf -->|"bug_to_fix_check<br/>✅ all Bugs Done?"| tc_bl
                  end
    
                  %% ─── QUESTIONS & ANSWERS ─────────────────────────────────────────────
                  subgraph QA["❓ Questions & Answers"]
                      direction LR
                      qa_q["story_questions<br/>📋 Subtask tickets + label q"]:::action
                      qa_d["po_refinement.json · AI PO<br/>🤖 reads question subtask + parent story<br/>💬 writes answer · closes subtask · 🏷️➖ q"]:::action
                      qa_q --> qa_d
                  end
    
                  %% ─── SM LABEL LOCKING ────────────────────────────────────────────────
                  subgraph LBL["🏷️ SM Label Locking"]
                      direction LR
                      lbl_a["SM adds sm_xxx_triggered<br/>BEFORE dispatch<br/>skipIfLabel = duplicate guard"]:::action
                      lbl_b["postJS releaseLock()<br/>removes sm_xxx_triggered<br/>AFTER completion"]:::action
                      lbl_wip["wip label<br/>pauses SM processing for this ticket"]:::action
                      lbl_a --> lbl_b
                  end
    
                  %% ─── HUMAN TOUCHPOINT ────────────────────────────────────────────────
                  subgraph HUMAN["👤 Human Touchpoint"]
                      direction LR
                      h_blocked["👤 Human reviews Blocked tickets<br/>only place where manual intervention<br/>is expected in the pipeline"]:::humanInput
                  end
    
                  %% ─── CROSS-PIPELINE CONNECTIONS ──────────────────────────────────────
                  in_feat -->|"Epics + Stories"| s_bl
                  in_bug_out -->|"Bug ticket"| b_rfd
    
                  s_bl -->|"story_questions · 🏷️➕ sm_story_questions_triggered"| QA
                  QA -->|"po_refinement answers done"| s_por
    
                  s_mrg -->|"test_cases_generator<br/>📋 Test Case tickets linked to Story"| tc_bl
                  b_rft -->|"bug_test_cases_generator<br/>📋 Test Case tickets linked to Bug"| tc_bl
    
                  tc_fai -->|"bug_creation<br/>🐛 Bug ticket linked to TC"| b_bl
                  b_don -->|"bug_to_fix_check<br/>TC → Backlog"| tc_bl
    
                  tc_blocked -->|"needs investigation"| h_blocked
                  s_blocked -->|"needs investigation"| h_blocked
                  b_blocked -->|"needs investigation"| h_blocked
            """;
    // ─────────────────────────────────────────────────────────────

    @Test
    void renderMyPresentation() throws Exception {
        Path outputDir = Path.of("build/my-presentation");
        Files.createDirectories(outputDir);

        MermaidRenderer renderer = new MermaidRenderer();

        Path svgPath = outputDir.resolve("diagram.svg");
        Path pngPath = outputDir.resolve("diagram.png");

        renderer.renderToSvgFileUnchecked(DIAGRAM, svgPath);
        System.out.println("✅ SVG → " + svgPath.toAbsolutePath());

        renderer.renderToPngUnchecked(DIAGRAM, pngPath);
        System.out.println("✅ PNG → " + pngPath.toAbsolutePath());
    }
}
