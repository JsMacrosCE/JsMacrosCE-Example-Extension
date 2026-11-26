package com.example.jsmacros;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.extensions.LanguageExtension;
import com.jsmacrosce.jsmacros.core.extensions.LibraryExtension;
import com.jsmacrosce.jsmacros.core.language.BaseLanguage;
import com.jsmacrosce.jsmacros.core.language.BaseWrappedException;
import com.jsmacrosce.jsmacros.core.library.BaseLibrary;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlInfo;
import org.apache.commons.jexl3.JexlScript;

import java.io.File;
import java.util.Locale;
import java.util.Set;

public final class ExampleExtension implements LanguageExtension, LibraryExtension {
    private static final String SCRIPT_SUFFIX = "jexl";
    private JexlLanguage language;

    @Override
    public String getExtensionName() {
        // Same id as jsmacrosce.ext.examplejexl.json
        return "examplejexl";
    }

    @Override
    public void init(Core<?, ?> runner) {
        // Wait until init so the bundled JEXL jars are available
        language = new JexlLanguage(this, runner);
    }

    @Override
    public Set<Class<? extends BaseLibrary>> getLibraries() {
        return Set.of(ExampleGreetingLibrary.class);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public ExtMatch extensionMatch(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith("." + SCRIPT_SUFFIX)
                ? ExtMatch.MATCH
                : ExtMatch.NOT_MATCH;
    }

    @Override
    public String defaultFileExtension() {
        return SCRIPT_SUFFIX;
    }

    @Override
    public BaseLanguage<?, ?> getLanguage(Core<?, ?> runner) {
        return language;
    }

    @Override
    public BaseWrappedException<?> wrapException(Throwable throwable) {
        // Let JsMacros handle errors we don't recognize
        if (!(throwable instanceof JexlException error)) {
            return null;
        }

        JexlInfo info = error.getInfo();
        BaseWrappedException.GuestLocation location = null;
        if (info != null) {
            String source = info.getName();
            File file = source == null || source.equals(JexlLanguage.INLINE_SOURCE) ? null : new File(source);
            location = new BaseWrappedException.GuestLocation(file, -1, -1, info.getLine(), info.getColumn());
        }
        return new BaseWrappedException<>(error, error.getMessage(), location, null);
    }

    @Override
    public boolean isGuestObject(Object value) {
        // Strings and numbers are plain Java values, even when they come from a script
        return value instanceof JexlScript;
    }
}
