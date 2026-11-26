package com.example.jsmacros;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.library.BaseLibrary;
import com.jsmacrosce.jsmacros.core.library.Library;

@Library("ExampleGreeting")
public final class ExampleGreetingLibrary extends BaseLibrary {
    public ExampleGreetingLibrary(Core<?, ?> runner) {
        super(runner);
    }

    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
