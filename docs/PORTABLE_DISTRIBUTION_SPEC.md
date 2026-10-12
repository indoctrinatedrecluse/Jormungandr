# Jörmungandr Distribution & Portable Architecture Specification

## Overview & Purpose
This document defines the verified, production-grade configuration for Jörmungandr standalone portable releases and installers (validated and confirmed functional in v1.3.0). 
Any future releases **MUST** adhere to this exact specification to guarantee that all bespoke branding, splash screens, custom extensions, themes, licensing subsystems, perspective switchers, and menus load deterministically on launch.

---

## 1. The Plugin Discovery Mechanism (IntelliJ Platform 2024.3+)

### Critical Discovery Rule
In modern IntelliJ Platform (2024.3+), plugins placed in `<IDE_HOME>/plugins/` are **NOT** automatically detected or loaded by directory traversal. The runtime module and classloading system strictly requires explicit registration in `product-info.json`.

If a plugin is omitted from `product-info.json`, the IDE will start up cleanly as vanilla IntelliJ Community Edition with zero errors, but none of the custom extensions, actions, perspective groups, themes, or app initializers will load.

### Requirements for `product-info.json`
For every bundled Jörmungandr plugin, two sections in `<IDE_HOME>/product-info.json` **MUST** be populated:

1. **`bundledPlugins` Array**:
   Must contain the plugin's exact `<id>` from its `plugin.xml`:
   ```json
   "bundledPlugins": [
     "org.jormungandr.ide",
     "org.jormungandr.jupyter",
     "org.jormungandr.dataframe",
     "org.jormungandr.database"
   ]
   ```

2. **`layout` Array**:
   Must declare an entry with `"kind": "plugin"` and the complete list of all `.jar` files in that plugin's `lib/` directory, using forward-slash paths relative to `<IDE_HOME>`:
   ```json
   [
     {
       "name": "org.jormungandr.ide",
       "kind": "plugin",
       "classPath": [
         "plugins/platform-shell/lib/annotations-13.0.jar",
         "plugins/platform-shell/lib/duckdb_jdbc-1.1.3.jar",
         "plugins/platform-shell/lib/error_prone_annotations-2.27.0.jar",
         "plugins/platform-shell/lib/extension-core-1.3.0.jar",
         "plugins/platform-shell/lib/gson-2.11.0.jar",
         "plugins/platform-shell/lib/kotlin-stdlib-2.1.0.jar",
         "plugins/platform-shell/lib/platform-shell-1.3.0.jar"
       ]
     },
     {
       "name": "org.jormungandr.jupyter",
       "kind": "plugin",
       "classPath": [
         "plugins/jupyter-integration/lib/jeromq-0.6.0.jar",
         "plugins/jupyter-integration/lib/jnacl-1.0.0.jar",
         "plugins/jupyter-integration/lib/jupyter-integration-1.3.0.jar"
       ]
     },
     {
       "name": "org.jormungandr.dataframe",
       "kind": "plugin",
       "classPath": [
         "plugins/dataframe-viewer/lib/dataframe-viewer-1.3.0.jar"
       ]
     },
     {
       "name": "org.jormungandr.database",
       "kind": "plugin",
       "classPath": [
         "plugins/database-suite/lib/database-suite-1.3.0.jar",
         "plugins/database-suite/lib/sqlite-jdbc-3.47.1.0.jar"
       ]
     }
   ]
   ```

`tools/patch_portable_ide.py` automatically handles dynamic discovery of these jars and patches `product-info.json` during the release packaging lifecycle.

---

## 2. Deterministic Portable Path Resolution

### The Pitfall: Relative `-Didea.*.path` in `.vmoptions`
- **NEVER** place relative paths like `-Didea.config.path=../portable-data/config` or `-Didea.plugins.path=../portable-data/plugins` into `bin/idea64.exe.vmoptions`.
- **Reason**: JVM command-line system properties override `idea.properties`. When relative, the JVM evaluates them against `user.dir` (the current working directory of the launching process). If the IDE is launched from Explorer, a desktop shortcut, PowerShell, or from outside `bin/`, `../portable-data` resolves outside the IDE installation directory, causing `NoSuchFileException` and breaking all user data and plugins.

### The Working Architecture: `${idea.home.path}` in `bin/idea.properties`
All path configuration **MUST** be defined in `bin/idea.properties` using the launcher-computed token `${idea.home.path}`:
```properties
# Jörmungandr Standalone Portable IDE Configuration
idea.config.path=${idea.home.path}/portable-data/config
idea.system.path=${idea.home.path}/portable-data/system
idea.plugins.path=${idea.home.path}/portable-data/plugins
idea.log.path=${idea.home.path}/portable-data/log
idea.vendor.name=indoctrinatedrecluse
idea.product.name=Jörmungandr
idea.application.name=Jörmungandr
idea.paths.selector=Jormungandr1.0
idea.platform.prefix=Idea
idea.required.plugins.id=org.jormungandr.ide
idea.fatal.error.notification=disabled
sun.java2d.uiScale.enabled=true
swing.bufferPerWindow=true
ide.ui.new.file.chooser=true
ide.save.settings.on.exit=true
```
Because `${idea.home.path}` is computed by the native C++ executable launcher (`idea64.exe` / `jormungandr64.exe`) and shell scripts from the location of `bin/..`, it **ALWAYS** resolves to the exact IDE root, regardless of where or how the process was invoked.

---

## 3. Launchers & Executables Structure

Every release distribution (both portable ZIP and installer) includes identical cloned launchers in `bin/` and root `.bat` scripts:

1. **Root Launchers**:
   - `jormungandr.bat` and `Jormungandr.bat`: Located in the IDE root. Automatically ensures `portable-data/` folders exist and launches `bin/jormungandr64.exe` or `bin/idea64.exe`.
2. **Binary Launchers in `bin/`**:
   - `bin/jormungandr.bat`: Shell launcher calling root `jormungandr.bat`.
   - `bin/idea64.exe`, `bin/idea.exe`, `bin/jormungandr64.exe`, `bin/jormungandr.exe`: All cloned from the native 64-bit launcher binary.
   - Corresponding `.vmoptions` files:
     - `bin/idea64.exe.vmoptions`
     - `bin/idea.exe.vmoptions`
     - `bin/jormungandr64.exe.vmoptions`
     - `bin/jormungandr.exe.vmoptions`
   All `.vmoptions` are kept strictly in sync, containing branding flags and **zero** relative path overrides.

---

## 4. Branding, ApplicationInfo, and Splash Screen Injection

To ensure Jörmungandr branding displays across all launch vectors (`jormungandr.bat`, `bin/idea.exe`, `bin/idea64.exe`):

1. **`lib/jormungandr-bootstrap.jar`**:
   - Packaged and placed in `<IDE_HOME>/lib/`.
   - Injected into `product-info.json` under `"bootClassPathJarNames"` right after `platform-loader.jar`.
   - Injected into `modules/module-descriptors.jar` inside `intellij.idea.community.customization.xml`.
   - Contains:
     - `idea/IdeaApplicationInfo.xml` with product name `Jörmungandr`, vendor `indoctrinatedrecluse`, build `JM-243.23654.117`, and essential plugin registrations.
     - Splash screens: `splash/splash.png`, `splash/splash@2x.png`, `idea_community_logo.png`.
     - Icons: `icons/jormungandr.svg`, `icons/jormungandr_16.svg`, `idea-ce.svg`.
2. **`lib/app.jar` Patching**:
   - `patch_portable_ide.py` directly patches `lib/app.jar` to replace `idea/IdeaApplicationInfo.xml` and splash/icon assets with Jörmungandr branding, ensuring native fallback consistency.

---

## 5. Licensing & Registry Infrastructure

Persistent storage for licenses and anti-tamper trials operates across two independent mechanisms:
1. **Windows Registry (Native)**:
   - `HKCU\Software\Jormungandr\Licensing`: Stores `LicenseKey`, `LicenseType`, `Username`, `Hwid`, `Status`, `ActivatedAt`, `ExpiresAt`, `TrialFirstActivatedAt`, `TrialChecksum`.
2. **Java Preferences API (Cross-platform fallback)**:
   - Node: `jormungandr/licensing` -> mapped to `HKCU\SOFTWARE\JavaSoft\Prefs\jormungandr\licensing`.
   - Vendor Node: `indoctrinatedrecluse/jormungandr` -> mapped to `HKCU\SOFTWARE\JavaSoft\Prefs\indoctrinatedrecluse\jormungandr`.

Both are verified operational. On IDE boot, `JormungandrAppInitializer` runs automatically to verify preferences, initialize registry hives, start the animated splash screen, register core extensions, and activate themes.

---

## 6. Packaging & Compression Standards

When packaging releases using `tools/package_release.ps1`:
- **Inno Setup Installer**:
  - Uses `Compression=lzma2/normal`, `LZMANumBlockThreads=8`, `LZMAUseSeparateProcess=yes` in `installer/jormungandr_setup.iss` to complete builds under ~80 seconds.
- **7-Zip Portable Archive**:
  - Uses `7z a -tzip -mx=5 -mmt=8` for fast multi-threaded compression.
- **Post-Patch Verification**:
  - `package_release.ps1` runs strict assertion checks on critical files (`jormungandr.bat`, `bin/idea.properties`, `bin/idea64.exe`, `lib/jormungandr-bootstrap.jar`, etc.) before archiving.

---

## 7. Checklist for Future Releases

Before publishing any new version `vX.Y.Z`:
1. [ ] Update version strings in `build.gradle.kts`, `package_release.ps1`, `installer/jormungandr_setup.iss`, and `CHANGELOG.md`.
2. [ ] Run `tools/patch_portable_ide.py` through `tools/package_release.ps1`.
3. [ ] Verify `product-info.json` contains all 4 custom plugins in `bundledPlugins` and `"layout"`.
4. [ ] Verify `bin/idea64.exe.vmoptions` contains **no** relative `-Didea.*.path` entries.
5. [ ] Verify `bin/idea.properties` contains `${idea.home.path}/portable-data/...`.
6. [ ] Verify both `jormungandr.bat` and `bin/idea.exe` launch the full Jörmungandr environment with all custom menus, perspectives, extensions, and themes visible.
