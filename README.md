# JsMacrosCE Example Extension

A standalone extension template for [JsMacrosCE 2.1.0](https://github.com/JsMacrosCE/JsMacros/releases/tag/v2.1.0).
It adds [Apache Commons JEXL](https://commons.apache.org/proper/commons-jexl/) as a
`.jexl` scripting language and provides a small `ExampleGreeting` library.

## Build and run it

1. Install JDK 21 and run `./gradlew build` (`gradlew.bat build` on Windows).
2. Put `build/libs/jsmacrosce-example-extension-1.0.0.jar` in your Minecraft
   instance's `config/jsMacros/Extensions/` and restart the client.
3. Copy [`hello.jexl`](examples/hello.jexl) into `config/jsMacros/Macros/` and
   run it from the JsMacros screen.

`./gradlew jar` builds the JAR. `./gradlew test` loads that JAR from `build/`
using the core included in the published API. The tests exercise JEXL and
library registration with a stand-in for the `Chat` library. They require no
Minecraft, Fabric, NeoForge, or full JsMacrosCE mod JAR.

## Set up your own extension

- **Rename the extension.**
  - Choose your own Java package and a unique extension ID.
  - Update the provider's fully qualified class name in
  [`META-INF/services/com.jsmacrosce.jsmacros.core.extensions.Extension`](src/main/resources/META-INF/services/com.jsmacrosce.jsmacros.core.extensions.Extension). Keep that service filename as-is.
  - Match `getExtensionName()` to the `jsmacrosce.ext.<id>.json` filename
  in [`build.gradle.kts`](build.gradle.kts). The ID and the script suffix
  are independent; this example uses `examplejexl` and `jexl`.
  - Set your project and JAR name in [`settings.gradle.kts`](settings.gradle.kts).
- **Adding a library to an existing language?**
  - Keep `LibraryExtension`, `getExtensionName()`, `init(Core<?, ?>)`, `getLibraries()`,
  and the service file.
  - Remove the `LanguageExtension` methods, engine classes and embedded
  runtime if you don't need them
  - See [`ExampleGreetingLibrary.java`](src/main/java/com/example/jsmacros/ExampleGreetingLibrary.java)
  for `@Library("YourGlobal")` and the public `Core` constructor. A `BaseLibrary`
  instance is shared; use `PerExecLibrary` with a public `BaseScriptContext<?>`
  constructor when each script needs its own state. Pick a distinct global name.
- **Adding a language?**
  - Implement `LanguageExtension` and choose a suffix that doesn't conflict with
  one users already have.
  - The extension can also implement `LibraryExtension` if
  it contributes libraries. Replace
  [`JexlLanguage.java`](src/main/java/com/example/jsmacros/JexlLanguage.java) and
  [`JexlScriptContext.java`](src/main/java/com/example/jsmacros/JexlScriptContext.java)
  with your adapter and add its runtime to `embedded` in the build.
  - Keep file and inline evaluation on the same binding path so both receive `retrieveLibs(...)`,
  `event`, `file`, and `context`. Inline runs may have no file, and either kind
  of run may have no event.

### What the loader expects

JsMacrosCE discovers public, no-argument providers through the service file.
It then loads declared dependency JARs before calling `init`, and registers the
library classes afterward. Keep the provider constructor and static initialization
independent of nested dependencies. If work needs other extensions to be fully
registered, use `onExtensionsLoaded`.

The standalone JAR contains a descriptor named `jsmacrosce.ext.examplejexl.json`
at its root and the engine's JARs under `META-INF/jsmacroscedeps/`. The build
generates the descriptor from the same resolved JARs it packages, including
JEXL's transitive Commons Logging dependency. A dependency-free extension can
omit the descriptor. Dependencies share a class loader with other extensions,
so keep host libraries out of `embedded` and check for version conflicts when
adding a runtime. If you need translations, put them under
`assets/jsmacrosce/<extension-id>/lang/<locale>.json`.

### What this engine adapter demonstrates

The JEXL engine is shared per provider, with a fresh set of bindings for each
script. JsMacrosCE owns the worker, event lock, and base context cleanup.
JEXL checks for interruption during evaluation, and the context recognizes
JEXL's cancellation exception after a requested stop. If your engine owns
threads or native resources, use its own cancellation and cleanup APIs as well.
JEXL reports source positions after its own whitespace normalization; the
adapter preserves those positions and lets JsMacrosCE handle unrelated Java errors.

This example exposes the JsMacrosCE packages to JEXL for trusted local macros.
If you expose classes from other packages, review the JEXL permissions for those
classes too. JEXL's Java interop is distinct from Graal's: this adapter does not
provide a `JavaWrapper` implementation for `JsMacros.on(...)` callbacks. A
language that supports callbacks needs its own `MethodWrapper` integration to
manage context lifetime and thread entry.

For larger integrations, see the [built-in Graal adapter and shipped Python runtime](https://github.com/JsMacrosCE/JsMacros/tree/v2.1.0/extension/graal).

## License

The template is available under the [MIT License](LICENSE). The bundled
dependencies retain their own licenses.
