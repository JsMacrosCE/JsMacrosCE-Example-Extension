package com.example.jsmacros;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.config.ScriptTrigger;
import com.jsmacrosce.jsmacros.core.event.BaseEvent;
import com.jsmacrosce.jsmacros.core.language.BaseLanguage;
import com.jsmacrosce.jsmacros.core.language.EventContainer;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.JexlInfo;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

final class JexlLanguage extends BaseLanguage<MapContext, JexlScriptContext> {
    static final String INLINE_SOURCE = "<inline JEXL>";

    // Share the thread-safe engine, give each run its own variables
    private final JexlEngine engine = new JexlBuilder()
            .features(JexlFeatures.createScript().pragma(false).newInstance(false))
            .permissions(JexlPermissions.RESTRICTED.compose("com.jsmacrosce.jsmacros.*", "com.example.jsmacros.*"))
            // Tell JsMacros the script stopped instead of returning normally
            .cancellable(true)
            .safe(false)
            .strict(true)
            .silent(false)
            // Don't reuse the wrong filename when two scripts have the same code
            .cache(0)
            .create();

    JexlLanguage(ExampleExtension extension, Core<?, ?> runner) {
        super(extension, runner);
    }

    @Override
    public JexlScriptContext createContext(BaseEvent event, File file) {
        return new JexlScriptContext(runner, event, file);
    }

    @Override
    protected void exec(EventContainer<JexlScriptContext> container, ScriptTrigger macro, BaseEvent event)
            throws IOException {
        execute(container, Files.readString(container.getCtx().getFile().toPath()));
    }

    @Override
    protected void exec(EventContainer<JexlScriptContext> container, String lang, String script, BaseEvent event) {
        execute(container, script);
    }

    // Same setup for script files and inline code
    private void execute(EventContainer<JexlScriptContext> container, String source) {
        JexlScriptContext context = container.getCtx();
        MapContext bindings = context.getContext();
        retrieveLibs(context).forEach(bindings::set);
        File file = context.getFile();
        bindings.set("event", context.getTriggeringEvent());
        bindings.set("file", file);
        bindings.set("context", container);

        String sourceName = file == null ? INLINE_SOURCE : file.getAbsolutePath();
        engine.createScript(new JexlInfo(sourceName, 1, 1), source).execute(bindings);
    }
}
