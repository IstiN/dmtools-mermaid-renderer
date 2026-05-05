package com.github.istin.dmtools.mermaid;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class MermaidDiagramSupport {

    enum Level {
        PRODUCTION,
        EXPERIMENTAL,
        UNSUPPORTED
    }

    private static final Set<String> PRODUCTION_TYPES = Set.of(
            "block",
            "flowchart",
            "mindmap",
            "pie",
            "quadrant",
            "radar",
            "sankey",
            "sequence",
            "timeline",
            "treemap",
            "treeview",
            "venn"
    );

    private static final Set<String> EXPERIMENTAL_TYPES = Set.of(
            "architecture",
            "c4",
            "class",
            "entity-relationship",
            "gantt",
            "git",
            "ishikawa",
            "kanban",
            "packet",
            "requirement",
            "state",
            "user-journey",
            "wardley",
            "xy"
    );

    private MermaidDiagramSupport() {
    }

    static String detectType(String definition) {
        String first = definition == null ? "" : definition.stripLeading().lines().findFirst().orElse("").trim();
        String normalized = first.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("flowchart") || normalized.startsWith("graph")) {
            return "flowchart";
        }
        if (normalized.startsWith("sequencediagram")) {
            return "sequence";
        }
        if (normalized.startsWith("classdiagram")) {
            return "class";
        }
        if (normalized.startsWith("erdiagram")) {
            return "entity-relationship";
        }
        if (normalized.startsWith("statediagram")) {
            return "state";
        }
        if (normalized.startsWith("mindmap")) {
            return "mindmap";
        }
        if (normalized.startsWith("architecture")) {
            return "architecture";
        }
        if (normalized.startsWith("block")) {
            return "block";
        }
        if (normalized.startsWith("c4")) {
            return "c4";
        }
        if (normalized.startsWith("gantt")) {
            return "gantt";
        }
        if (normalized.startsWith("gitgraph")) {
            return "git";
        }
        if (normalized.startsWith("ishikawa")) {
            return "ishikawa";
        }
        if (normalized.startsWith("kanban")) {
            return "kanban";
        }
        if (normalized.startsWith("packet")) {
            return "packet";
        }
        if (normalized.startsWith("pie")) {
            return "pie";
        }
        if (normalized.startsWith("quadrantchart")) {
            return "quadrant";
        }
        if (normalized.startsWith("radar")) {
            return "radar";
        }
        if (normalized.startsWith("requirementdiagram")) {
            return "requirement";
        }
        if (normalized.startsWith("sankey")) {
            return "sankey";
        }
        if (normalized.startsWith("timeline")) {
            return "timeline";
        }
        if (normalized.startsWith("treeview")) {
            return "treeview";
        }
        if (normalized.startsWith("treemap")) {
            return "treemap";
        }
        if (normalized.startsWith("journey")) {
            return "user-journey";
        }
        if (normalized.startsWith("venn")) {
            return "venn";
        }
        if (normalized.startsWith("wardley")) {
            return "wardley";
        }
        if (normalized.startsWith("xychart")) {
            return "xy";
        }
        if (normalized.startsWith("zenuml")) {
            return "zenuml";
        }
        return Pattern.compile("\\s+").matcher(normalized).replaceAll("-");
    }

    static Level level(String type) {
        if (PRODUCTION_TYPES.contains(type)) {
            return Level.PRODUCTION;
        }
        if (EXPERIMENTAL_TYPES.contains(type)) {
            return Level.EXPERIMENTAL;
        }
        return Level.UNSUPPORTED;
    }

    static void requireProductionSupport(String definition) {
        String type = detectType(definition);
        Level level = level(type);
        if (level != Level.PRODUCTION) {
            throw new IllegalArgumentException("Mermaid diagram type '" + type + "' is " + level
                    + " in the GraalJS renderer. Production-supported types: " + PRODUCTION_TYPES);
        }
    }
}
