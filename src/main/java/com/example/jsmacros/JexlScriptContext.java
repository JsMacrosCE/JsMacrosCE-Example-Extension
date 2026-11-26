package com.example.jsmacros;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.event.BaseEvent;
import com.jsmacrosce.jsmacros.core.language.BaseScriptContext;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.MapContext;

import java.io.File;

final class JexlScriptContext extends BaseScriptContext<MapContext> {
    JexlScriptContext(Core<?, ?> runner, BaseEvent event, File file) {
        super(runner, event, file);
        // It's just a map of script variables, so there's nothing extra to close.
        // BaseScriptContext interrupts the run's threads and unblocks waiting events.
        setContext(new MapContext());
    }

    @Override
    public boolean isMultiThreaded() {
        // One thread at a time per variable map
        return false;
    }

    @Override
    protected boolean isLanguageCancellation(Throwable failure) {
        // After a stop request, this is expected and shouldn't be logged as an error
        return failure instanceof JexlException.Cancel;
    }
}
