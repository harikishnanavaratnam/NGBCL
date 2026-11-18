package lk.ngbcl.shared.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL slugs: "Kasun Perera" → "kasun-perera", with "-2", "-3"… when taken. */
public final class Slugs {

    private Slugs() {
    }

    public static String of(String text) {
        String ascii = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    public static String unique(String text, Predicate<String> taken) {
        String base = of(text);
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
