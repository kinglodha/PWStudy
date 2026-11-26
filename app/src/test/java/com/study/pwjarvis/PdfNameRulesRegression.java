package com.study.pwjarvis;

/** Dependency-free regression checks for offline PDF naming and CSV export. */
public final class PdfNameRulesRegression {
    private PdfNameRulesRegression() {}

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "] but got [" + actual + "]");
        }
    }

    private static void invalid(String name) {
        try {
            PdfNameRules.validatedName(name);
            throw new AssertionError("Accepted invalid PDF name: " + name);
        } catch (IllegalArgumentException expected) {
            // Invalid names must never reach the rename operation.
        }
    }

    public static void main(String[] args) {
        equal("Unnamed.pdf", PdfNameRules.suggest(null));
        equal("Notes.pdf", PdfNameRules.suggest("Notes.PDF.pdf"));
        equal("Chapter 1.pdf", PdfNameRules.suggest(" Chapter   1.pdf "));
        equal("Biology.pdf", PdfNameRules.validatedName("Biology"));
        equal("Chemistry.PDF", PdfNameRules.validatedName("Chemistry.PDF"));
        invalid("../escape.pdf");
        invalid("folder/name.pdf");
        invalid(" ");
        invalid("a|b.pdf");
        equal("Physics", PdfNameRules.subject("Kinematics.pdf", null));
        equal("Chemistry", PdfNameRules.subject("Mole Concept.pdf", null));
        equal("Biology", PdfNameRules.subject("Genetics.pdf", null));
        equal("Needs review", PdfNameRules.subject("Physics and Chemistry.pdf", null));
        equal("\"' =SUM(1,1)\"", PdfNameRules.csv(" =SUM(1,1)"));
        equal("\"safe\"\"name\"", PdfNameRules.csv("safe\"name"));
        System.out.println("PDF filename and CSV regression checks passed");
    }
}
