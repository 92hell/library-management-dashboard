package com.helmifr.library.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every message the server can show is translated: the English templates in the code (exceptions,
 * notifications, messages.get, login errors) must all be in i18n/messages_id.json, with the same
 * {0}-style placeholders; and validation message keys must exist in English and Indonesian.
 */
@DisplayName("Server messages (English / Bahasa Indonesia)")
class ServerMessagesTest {

    /** Calls whose string literals are message templates. */
    private static final Pattern CALL = Pattern.compile(
            "new (ConflictException|NotFoundException|FieldValidationException|ServiceUnavailableException)\\("
                    + "|\\b(notifyMember|notifyLibrarians|notifyAccount|setAside|problem|messages\\.get|Messages\\.english)\\(");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    static Map<String, String> indonesian;
    static List<Path> sources;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = ServerMessagesTest.class.getResourceAsStream("/i18n/messages_id.json")) {
            indonesian = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** The argument list of the call starting at {@code open} (the index of its '('). */
    private static String argumentsOf(String code, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return "";
    }

    private record Found(Set<String> templates, List<String> dynamic) {}

    private static Found scan() throws IOException {
        Set<String> templates = new TreeSet<>();
        List<String> dynamic = new ArrayList<>();
        for (Path file : sources) {
            String code = Files.readString(file);
            Matcher call = CALL.matcher(code);
            while (call.find()) {
                String name = call.group(1) != null ? call.group(1) : call.group(2);
                String args = argumentsOf(code, call.end() - 1);
                if (args.contains(".formatted(") || args.contains("String.format(")) {
                    dynamic.add(file.getFileName() + ": " + name + "(" + args.strip().replaceAll("\\s+", " ") + ")");
                }
                List<String> literals = new ArrayList<>();
                Matcher lit = LITERAL.matcher(args);
                while (lit.find()) {
                    literals.add(lit.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
                }
                if (literals.isEmpty()) {
                    continue;
                }
                if (name.equals("FieldValidationException")) {
                    literals = literals.subList(1, literals.size()); // the first literal is the field name
                }
                if (name.equals("NotFoundException") && literals.size() == 1 && !literals.get(0).contains("{")
                        && args.strip().matches("\"[^\"]*\"\\s*,\\s*[^\"]+")) {
                    templates.add(literals.get(0) + " {0} not found"); // new NotFoundException("Book", id)
                    continue;
                }
                if (name.equals("problem")) {
                    literals = literals.stream().filter(l -> !l.isBlank()).toList();
                }
                templates.addAll(literals);
            }
        }
        return new Found(templates, dynamic);
    }

    private static Set<String> placeholders(String s) {
        Matcher m = Pattern.compile("\\{(\\d+)}").matcher(s);
        Set<String> found = new TreeSet<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("messages are templates with {0}-style values, never built with String.formatted")
    void noDynamicTemplates() throws IOException {
        assertThat(scan().dynamic()).isEmpty();
    }

    @Test
    @DisplayName("every message the server shows has an Indonesian translation")
    void everyTemplateTranslated() throws IOException {
        assertThat(scan().templates().stream().filter(t -> !indonesian.containsKey(t)).toList()).isEmpty();
    }

    @Test
    @DisplayName("no leftover translations for messages the server no longer shows")
    void noUnusedTranslations() throws IOException {
        Set<String> used = scan().templates();
        assertThat(indonesian.keySet().stream().filter(k -> !used.contains(k)).toList()).isEmpty();
    }

    @Test
    @DisplayName("translations keep the same {0} placeholders")
    void samePlaceholders() {
        assertThat(indonesian.entrySet().stream()
                .filter(e -> !placeholders(e.getKey()).equals(placeholders(e.getValue())))
                .map(Map.Entry::getKey).toList()).isEmpty();
    }

    @Test
    @DisplayName("validation messages exist in English and Indonesian for every constraint used")
    void validationMessages() throws IOException {
        Properties english = bundle("/ValidationMessages.properties");
        Properties bahasa = bundle("/ValidationMessages_id.properties");
        Set<String> constraints = new TreeSet<>();
        Set<String> customKeys = new TreeSet<>();
        for (Path file : sources) {
            String code = Files.readString(file);
            Matcher a = Pattern.compile("@(NotBlank|NotNull|NotEmpty|Size|Email|Past|PastOrPresent|Future|FutureOrPresent"
                    + "|Min|Max|Pattern|AssertTrue|Positive|PositiveOrZero)\\b").matcher(code);
            while (a.find()) {
                constraints.add("jakarta.validation.constraints." + a.group(1) + ".message");
            }
            Matcher k = Pattern.compile("message\\s*=\\s*\"\\{([^}]+)}\"").matcher(code);
            while (k.find()) {
                customKeys.add(k.group(1));
            }
            assertThat(code).as("custom validation messages are {keys}, not English text: " + file)
                    .doesNotContainPattern("message\\s*=\\s*\"[^{\"]");
        }
        constraints.removeIf(key -> key.endsWith(".Pattern.message") || key.endsWith(".AssertTrue.message")); // custom
        assertThat(constraints.stream().filter(key -> !bahasa.containsKey(key)).toList()).isEmpty();
        assertThat(customKeys.stream().filter(key -> !english.containsKey(key) || !bahasa.containsKey(key)).toList())
                .isEmpty();
        assertThat(english.stringPropertyNames().stream().filter(key -> !bahasa.containsKey(key))
                .collect(Collectors.toList())).isEmpty();
    }

    private static Properties bundle(String path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = ServerMessagesTest.class.getResourceAsStream(path)) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return p;
    }
}
