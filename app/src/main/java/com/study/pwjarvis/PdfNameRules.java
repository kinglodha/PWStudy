package com.study.pwjarvis;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative filename suggestions. Nothing is applied without user approval. */
public final class PdfNameRules {
    private PdfNameRules() {}

    private static final Pattern PHYSICS = Pattern.compile(
            "\\b(physics|mechanics|kinematics|thermodynamics|electrostatics|optics|magnetism|gravitation|saleem)\\b");
    private static final Pattern CHEMISTRY = Pattern.compile(
            "\\b(chemistry|organic|inorganic|stoichiometry|mole concept|chemical bonding|periodic table|hydrocarbons)\\b");
    private static final Pattern BIOLOGY = Pattern.compile(
            "\\b(biology|botany|zoology|biomolecules|photosynthesis|respiration|genetics|cell the unit of life|plant kingdom|animal kingdom)\\b");

    public static String suggest(String name) {
        if (name == null) return "Unnamed.pdf";
        String cleaned = Normalizer.normalize(name, Normalizer.Form.NFC);
        cleaned = cleaned.replaceAll("(?i)(\\.pdf)+$", "");
        cleaned = cleaned.replaceAll("\\[\\s*@[^\\]]+\\]", " ");
        cleaned = cleaned.replaceAll("(?<!\\w)@[\\w.]+", " ");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 120) {
            int end = 120;
            if (Character.isHighSurrogate(cleaned.charAt(end - 1))) end--;
            cleaned = cleaned.substring(0, end).trim();
        }
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            cleaned = "Unnamed";
        }
        return cleaned + ".pdf";
    }

    public static String subject(String name, String location) {
        String value = ((location == null ? "" : location) + " " +
                (name == null ? "" : name)).toLowerCase(Locale.ROOT);
        value = value.replace('_', ' ').replace('-', ' ');
        boolean physics = PHYSICS.matcher(value).find();
        boolean chemistry = CHEMISTRY.matcher(value).find();
        boolean biology = BIOLOGY.matcher(value).find();
        int matches = (physics ? 1 : 0) + (chemistry ? 1 : 0) + (biology ? 1 : 0);
        if (matches != 1) return "Needs review";
        return physics ? "Physics" : chemistry ? "Chemistry" : "Biology";
    }

    public static String validatedName(String value) {
        if (value == null) throw new IllegalArgumentException("Enter a filename");
        String name = value.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..") ||
                name.matches(".*[\\p{Cntrl}/\\\\:*?\"<>|].*")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 240) {
            throw new IllegalArgumentException("Filename is too long");
        }
        return name;
    }

    public static String csv(String value) {
        String s = value == null ? "" : value;
        // Avoid spreadsheet formula interpretation of untrusted document names.
        if (s.matches("^[\\s]*[=+@-].*") || s.startsWith("\t") || s.startsWith("\r")) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
