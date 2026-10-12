# Release Packaging & Portable Distribution Rule

## Mandatory Distribution Standards
When preparing releases, modifying build scripts, or packaging Jörmungandr portable ZIPs or installers, all AI agents **MUST** strictly adhere to the following rules (documented in detail in `docs/PORTABLE_DISTRIBUTION_SPEC.md`):

1. **IntelliJ 2024.3+ Plugin Discovery (`product-info.json`)**:
   - Every bundled plugin (`platform-shell`, `jupyter-integration`, `dataframe-viewer`, `database-suite`) **MUST** be explicitly registered in `product-info.json`:
     - Inside `"bundledPlugins"` array with its plugin ID (`org.jormungandr.ide`, `org.jormungandr.jupyter`, `org.jormungandr.dataframe`, `org.jormungandr.database`).
     - Inside `"layout"` array with `kind: "plugin"` and all `.jar` paths listed under `classPath` (`plugins/<name>/lib/<jar>`).
   - Failure to do this causes IntelliJ to ignore the plugins, stripping all branding, custom menus, perspectives, and extensions.

2. **Deterministic Portable Pathing (`bin/idea.properties`)**:
   - All portable directory paths (`config`, `system`, `plugins`, `log`) **MUST** be defined in `bin/idea.properties` using `${idea.home.path}/portable-data/...`.
   - **NEVER** define relative `-Didea.*.path=../portable-data/...` in any `.vmoptions` file. Relative paths in `.vmoptions` break path resolution depending on process working directory.

3. **Multi-Vector Launch Support**:
   - The distribution must function identically whether launched from:
     - `jormungandr.bat` / `Jormungandr.bat` in IDE root.
     - `bin/jormungandr.bat`.
     - `bin/idea.exe` or `bin/idea64.exe`.
     - `bin/jormungandr.exe` or `bin/jormungandr64.exe`.
   - Keep all `.vmoptions` synchronized in `bin/` and keep launchers in sync via `tools/patch_portable_ide.py`.

4. **Multi-Threaded Compression**:
   - Keep Inno Setup installer compilation under 90 seconds using `Compression=lzma2/normal`, `LZMANumBlockThreads=8`, and `LZMAUseSeparateProcess=yes`.
   - Keep 7-Zip portable archive compression under 90 seconds using `-mx=5 -mmt=8`.
