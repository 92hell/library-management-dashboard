package com.helmifr.library.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Server messages in the reader's language (English or Bahasa Indonesia, from the request's
 * Accept-Language). The English template is the key, as in the frontend: {@code "Book {0} not found"}.
 * Indonesian texts live in {@code src/main/resources/i18n/messages_id.json}; ServerMessagesTest
 * fails when a template used in the code has no translation, or a translation is no longer used.
 */
@Component
public class Messages {

    private static final Map<String, String> INDONESIAN = load();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** Status values (LoanStatus, FineStatus) as words in Indonesian messages; English keeps the code. */
    private static final Map<String, String> INDONESIAN_STATUS = Map.ofEntries(
            Map.entry("PENDING", "diajukan"), Map.entry("WAITLISTED", "dalam antrean"),
            Map.entry("READY", "siap diambil"), Map.entry("ACTIVE", "dipinjam"),
            Map.entry("RETURNED", "dikembalikan"), Map.entry("REJECTED", "ditolak"),
            Map.entry("CANCELLED", "dibatalkan"), Map.entry("EXPIRED", "tidak diambil"),
            Map.entry("UNPAID", "belum dibayar"), Map.entry("PAID", "lunas"), Map.entry("WAIVED", "dihapuskan"));

    private static final DateTimeFormatter INDONESIAN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id"));

    /** In the current request's language. */
    public String get(String template, Object... args) {
        return render(template, args, LocaleContextHolder.getLocale());
    }

    /** In English, the language of logs, audit entries, emails and the stored text. */
    public static String english(String template, Object... args) {
        return render(template, args, Locale.ENGLISH);
    }

    public static String render(String template, Object[] args, Locale locale) {
        boolean indonesian = "id".equals(locale.getLanguage()) || "in".equals(locale.getLanguage());
        String text = indonesian ? INDONESIAN.getOrDefault(template, template) : template;
        if (args == null || args.length == 0) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            String value = i < args.length ? display(args[i], indonesian) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Values as text; dates as "17 Nov 2026" in Indonesian (ISO, as before, in English). */
    private static String display(Object value, boolean indonesian) {
        if (value == null) {
            return "";
        }
        if (indonesian && value instanceof Enum<?> e) {
            return INDONESIAN_STATUS.getOrDefault(e.name(), e.name());
        }
        if (indonesian && value instanceof LocalDate date) {
            return INDONESIAN_DATE.format(date);
        }
        String s = value.toString();
        if (indonesian && ISO_DATE.matcher(s).matches()) {
            return INDONESIAN_DATE.format(LocalDate.parse(s));
        }
        return s;
    }

    static Map<String, String> indonesian() {
        return INDONESIAN;
    }

    private static Map<String, String> load() {
        try (InputStream in = Messages.class.getResourceAsStream("/i18n/messages_id.json")) {
            return in == null ? Map.of() : new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read i18n/messages_id.json", e);
        }
    }
}
