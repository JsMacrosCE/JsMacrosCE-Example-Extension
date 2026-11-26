package com.example.jsmacros;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.config.BaseProfile;
import com.jsmacrosce.jsmacros.core.config.ScriptTrigger;
import com.jsmacrosce.jsmacros.core.event.BaseEvent;
import com.jsmacrosce.jsmacros.core.event.BaseEventRegistry;
import com.jsmacrosce.jsmacros.core.language.BaseWrappedException.GuestLocation;
import com.jsmacrosce.jsmacros.core.language.EventContainer;
import com.jsmacrosce.jsmacros.core.library.BaseLibrary;
import com.jsmacrosce.jsmacros.core.library.Library;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstalledExtensionTest {
    private static Core<TestProfile, TestRegistry> core;
    private static Path macros;
    private static ClassLoader originalLoader;

    @BeforeAll
    static void installExtension() throws Exception {
        // Catch missing files in the jar by keeping these classes off the test classpath
        ClassLoader parent = Core.class.getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> parent.loadClass("com.example.jsmacros.ExampleExtension"));
        assertThrows(ClassNotFoundException.class, () -> parent.loadClass("org.apache.commons.jexl3.JexlEngine"));

        // Keep test files under build/ since the loader leaves the jars open
        Path config = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "test-config-");
        Path extensions = Files.createDirectory(config.resolve("Extensions"));
        Path jar = Path.of(System.getProperty("example.extensionJar"));
        Files.copy(jar, extensions.resolve(jar.getFileName()));
        macros = config.resolve("Macros");
        core = new Core<>(TestRegistry::new, TestProfile::new, config.toFile(), macros.toFile(),
                LoggerFactory.getLogger(InstalledExtensionTest.class));
        core.libraryRegistry.addLibrary(TestChat.class);

        originalLoader = Thread.currentThread().getContextClassLoader();
        core.extensions.loadExtensions();
    }

    @AfterAll
    static void restoreContextClassLoader() {
        if (originalLoader != null) {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @BeforeEach
    void clearChat() {
        TestChat.messages.clear();
    }

    @Test
    void runsThePackagedLanguageAndLibraryFromAFileTrigger() throws Exception {
        Path file = Files.copy(Path.of("examples/hello.jexl"), macros.resolve("hello.jexl"));
        var language = core.extensions.getExtensionForFile(file.toFile());
        assertNotNull(language);
        assertSame(core.extensions.getExtensionForName("examplejexl"), language);
        assertEquals("jexl", language.defaultFileExtension());

        runFile(file.getFileName());

        assertIterableEquals(List.of("Hello, Ada!", "Hello, Grace!", "Hello, Linus!",
                "[PASS] 3 greetings; trigger: no event"), TestChat.messages);
    }

    @Test
    void suppliesFreshBindingsAndOptionalContextMetadataToSourceRuns() throws Exception {
        File origin = macros.resolve("origin.jexl").toFile();
        BaseEvent event = new BaseEvent(core);
        var first = runSource("""
                Chat.log(event.getEventName());
                if (file != context.getCtx().getFile()) { throw 'wrong file binding'; }
                """, origin, event);
        assertEquals("BaseEvent", TestChat.messages.poll());

        var second = runSource("""
                if (event != null || file != null) { throw 'unexpected file or event'; }
                Chat.log(ExampleGreeting.greet('inline'));
                """, null, null);
        assertEquals("Hello, inline!", TestChat.messages.poll());
        assertNotSame(first.getCtx().getContext(), second.getCtx().getContext());
    }

    @Test
    void reportsGuestSourceLocationForAnError() throws Exception {
        File origin = macros.resolve("broken.jexl").toFile();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var run = core.exec("jexl", "var x = 1;\nmissingFunction();", origin, null, null, failure::set);
        await(run);

        Throwable error = failure.get();
        assertNotNull(error);
        var diagnostic = core.wrapException(error);
        assertTrue(diagnostic.message.contains("missingFunction"));
        GuestLocation location = assertInstanceOf(GuestLocation.class, diagnostic.location);
        assertEquals(origin, location.file);
        assertEquals(2, location.line);
    }

    @Test
    void treatsInterruptAfterAnIntentionalStopAsCancellation() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var run = core.exec("jexl", """
                Chat.log('starting loop');
                while (true) { }
                """, null, null, null, failure::set);

        try {
            assertEquals("starting loop", TestChat.messages.poll(10, TimeUnit.SECONDS),
                    () -> "script did not reach its loop; reported failure: " + failure.get());
            assertTrue(run.getCtx().beginScriptStop());
        } finally {
            run.getCtx().closeContext();
            await(run);
        }
        assertNull(failure.get(), () -> "stop was reported as an error: " + failure.get());
    }

    @Test
    void canStopFromInsideTheScript() throws Exception {
        var run = runSource("""
                JsMacros.stopScript();
                Chat.log('execution continued after stop');
                """, null, null);

        assertTrue(run.getCtx().isScriptStopRequested());
        assertNull(TestChat.messages.poll());
    }

    private static void runFile(Path relativePath) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var trigger = new ScriptTrigger(ScriptTrigger.TriggerType.EVENT, "Example", relativePath, true, false);
        await(core.exec(trigger, null, null, failure::set));
        assertNull(failure.get(), () -> "file script failed: " + failure.get());
    }

    private static EventContainer<?> runSource(String source, File origin, BaseEvent event) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var run = core.exec("jexl", source, origin, event, null, failure::set);
        await(run);
        assertNull(failure.get(), () -> "inline script failed: " + failure.get());
        return run;
    }

    private static void await(EventContainer<?> run) throws InterruptedException {
        // Wait for the thread so we don't check cleanup too early
        assertTrue(run.getCtx().getMainThread().join(Duration.ofSeconds(10)), "script did not finish");
        assertFalse(run.isLocked(), "event lock was not released");
        assertTrue(run.getCtx().isContextClosed(), "script context is still open");
    }

    @Library("Chat")
    public static final class TestChat extends BaseLibrary {
        static final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        public TestChat(Core<?, ?> runner) {
            super(runner);
        }

        public void log(Object message) {
            messages.add(String.valueOf(message));
        }
    }

    private static final class TestProfile extends BaseProfile {
        TestProfile(Core<?, ?> runner, Logger logger) {
            super(runner, logger);
        }

        @Override
        public void logError(Throwable error) {
            LOGGER.error("Unexpected script failure", error);
        }

        @Override
        public boolean checkJoinedThreadStack() {
            return false;
        }
    }

    private static final class TestRegistry extends BaseEventRegistry {
        TestRegistry(Core<?, ?> runner) {
            super(runner);
        }

        @Override
        public void addScriptTrigger(ScriptTrigger trigger) {
        }

        @Override
        public boolean removeScriptTrigger(ScriptTrigger trigger) {
            return false;
        }

        @Override
        public List<ScriptTrigger> getScriptTriggers() {
            return List.of();
        }
    }
}
