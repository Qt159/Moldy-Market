package com.moldy.moldymarket.product.search;


import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchTextNormalizer {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private SearchTextNormalizer() {}
    /** "Điện thoại iPhone 13 (Pro)" -> "dien thoai iphone 13 pro" */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String s = input.toLowerCase(Locale.ROOT).replace('đ', 'd');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        return s.trim();
    }

}
