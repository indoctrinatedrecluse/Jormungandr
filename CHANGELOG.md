# Changelog

All notable changes to the **Jörmungandr IDE** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-10-12

### 🚀 Complete Brand Overhaul & Distribution Pipeline Polish

The **v1.4.0** release delivers full white-label branding across the IDE Welcome Screen and Project Selection frames, accelerated multi-threaded release packaging, and streamlined distribution governance.

---

### 🌟 Key Feature Highlights

#### 🐍 1. Bespoke Welcome Screen & Project Selection Screen Rebranding
- **Complete Visual Overhaul**: Replaced upstream "Welcome to IntelliJ IDEA" branding with "Welcome to Jörmungandr" across the project selection frame, tab cards, empty-state panels, and window headers.
- **Dynamic Hierarchy Interception**: Built recursive `ContainerListener` monitoring into `JormungandrBranding` that dynamically captures and rebrands asynchronously mounted card panels, recent project lists, and empty-state project templates (`EmptyStateProjectsPanel`) as soon as they mount to the DOM tree.
- **Bespoke Logo Integration**: Automatically swaps upstream icons in header and splash panels with high-resolution Jörmungandr serpent emblems (`LOGO_32`, `LOGO_64`, `APP_ICON`), patching `app-client.jar`, `app.jar`, and `jormungandr-bootstrap.jar` with `idea_logo_welcome.png` replacements.
- **`AppLifecycleListener.welcomeScreenDisplayed` Hook**: Early lifecycle hook in `JormungandrShutdownListener` that immediately applies window branding and dismisses the animated splash screen when the Welcome Screen opens.
- **Service Container Reflection**: Expanded `patchApplicationInfo()` to mutate the active `ApplicationInfo` service instance registered in `ApplicationManager.getApplication().getService(ApplicationInfo::class.java)` in addition to `getShadowInstance()`.

#### ⚡ 2. High-Performance Multi-Threaded Compression Pipeline
- **Parallel Deflate Acceleration**: Upgraded `tools/package_release.ps1` to leverage 7-Zip / NanaZip multi-threading (`-mx=3 -mmt=16`), reducing standalone portable package archive times from ~17 minutes to under 2 minutes.
- **Smart Delta Patch Pipeline**: Automatic generation of differential update bundles (`generate_patch.ps1`) targeting baseline archives for lightweight network updates.
- **Turnkey Packaging & Verification**: Automated post-patch sanity verification checking launcher batch files, properties, VM options, and bootstrap classpath jars before archive creation.

#### 🧹 3. Release Lineage & Asset Governance
- **Distribution Pruning**: Cleaned up historical pre-v1.3.0 releases and tags on GitHub to maintain a clean, active distribution footprint.
- **Production Spec Compliance**: Fully synchronized with `docs/PORTABLE_DISTRIBUTION_SPEC.md` and `.agents/rules/packaging.md`.

---

## [1.3.0] - 2026-10-12

### 🚀 Major Feature & Reliability Release

The **v1.3.0** release delivers deep workflow enhancements for Data Scientists, ML Engineers, and Data Platform Architects, alongside critical lifecycle stability fixes and passive update safeguards.

---

### 🌟 Key Feature Highlights

#### 🛠️ 1. Elimination of "Saving Settings" IDE Exit Hang
- **Daemon Lifecycle Enforcement**: All background polling loops (`HardwareTelemetryService`, ZeroMQ heartbeats, Python kernel monitors) are now decoupled with non-blocking daemon threads and cooperative shutdown flags (`AtomicBoolean`).
- **Cooperative `AppLifecycleListener` Hook**: Added `JormungandrShutdownListener` hooking into IntelliJ's `appClosing()` phase to proactively disconnect ZeroMQ sockets and terminate running kernel sessions cleanly before IDE settings persistence executes.
- **Fast Process Tree Termination**: `SubprocessPythonSession.kt` gracefully sends `SIGTERM` / `SIGINT` to Python and kernel processes with a 1.5s timeout before issuing non-blocking process tree destruction (`destroyForcibly()`), preventing zombie subprocesses from holding file locks on workspace `.xml` files.

#### 🔄 2. Passive Auto-Update Notification & Rollback Safeguards
- **Non-blocking Startup Check**: `PassiveUpdateCheckListener` queries GitHub Releases asynchronously on IDE startup without delaying UI rendering.
- **Notification Balloons**: Surfaces actionable update notifications with options to inspect release notes or trigger the one-click patch updater.
- **Rollback Snapshots**: `jormungandr-updater.ps1` now automatically creates a timestamped zip backup snapshot (`backup_<timestamp>.zip`) of modified directories before applying any patch, enabling instantaneous rollback if patch application is interrupted.

#### 🔬 3. Variable & PyTorch Tensor Inspector Tool Window
- **Interactive Tool Window**: Dedicated docked tool window (`Variable Inspector`) providing live variable introspection across active Jupyter notebooks and Python REPL sessions.
- **Dedicated Inspector Tabs**: Separate views for **DataFrames**, **PyTorch Tensors**, **NumPy Arrays**, and **Scalars / Primitives**.
- **Deep Tensor Telemetry**: Displays tensor shapes, memory footprints, dtypes, device placement (`cuda:0`, `cpu`, `mps`), min/mean/max stats, gradient tracking flags (`requires_grad`), and layout details.
- **Native Polars & Pandas Introspection**: Inspect schema types, row counts, and null distributions directly from the variable tree.

#### 🤖 4. Inline Ghost-Text AI Autocomplete (Ollama / vLLM FIM)
- **Fill-In-the-Middle (FIM) Integration**: Full support for FIM prompting (`<PRE>`, `<SUF>`, `<MID>`) optimized for DeepSeek-Coder, CodeLlama, StarCoder, and Qwen2.5-Coder.
- **Native Platform Inline Completion**: Non-blocking `JormungandrInlineCompletionProvider` rendering faint ghost-text inline completions in real-time as you type.
- **Smooth Muscle Memory**: Single-keystroke `Tab` acceptance and `Esc` dismissal with debounced query dispatching to avoid saturating local GPU inference daemons.

#### 📊 5. Evidently-Style Dataset Drift Studio & Perturbation Simulator
- **Reference vs Current Dataset Comparator**: Visual side-by-side distribution analysis comparing baseline training data against live production inference batches.
- **Multi-Test Statistical Scorecard**: Integrated scoring combining Population Stability Index (PSI), Two-Sample Kolmogorov-Smirnov (K-S) test, Wasserstein Distance, and Jensen-Shannon Divergence.
- **Synthetic Perturbation Simulator**: Stress-test data quality and pipeline drift triggers by interactively injecting synthetic drift (mean shifts, variance scaling, missing value spikes, categorical flips) directly within the IDE.

#### 🧊 6. Visual Lakehouse Time-Travel Snapshot Scrubber & History Replay
- **Interactive Timeline Scrubber**: Visual commit timeline for Delta Lake (`_delta_log/*.json`) and Apache Iceberg metadata catalogs.
- **Snapshot Diff Inspection**: Compare file additions, removals, and schema evolutions between arbitrary snapshot commits.
- **Animated History Replay**: Step through lakehouse table mutations chronologically with animated replay to visualize data ingestion and compaction cycles over time.

#### 🗺️ 7. Visual Query Execution Plan DAG (`EXPLAIN ANALYZE`)
- **Interactive Plan DAG Canvas**: Parses and visualizes DuckDB and PostgreSQL `EXPLAIN (ANALYZE)` query plans as interactive 2D directed acyclic graphs.
- **Cost & Timing Breakdown**: Node-level metrics detailing execution time, operator cost percentages, and estimated vs. actual row counts.
- **Bottleneck Badging**: Visual warning badges highlighting expensive sequential scans, nested loop joins, high-memory hash joins, and sorting bottlenecks.

#### ⚡ 8. Astral `uv` Python Environment Manager & Status Bar Widget
- **First-Class `uv` Support**: Native detection of Astral's ultra-fast `uv` package manager and virtual environment orchestrator.
- **Status Bar Environment Widget**: Real-time display of the active Python interpreter, environment kind (`uv`, `conda`, `venv`, `system`), and version.
- **1-Click Sync**: Synchronize dependencies (`uv sync`), inspect installed packages (`uv pip list`), and create virtual environments directly from the IDE.

#### 🌊 9. Expanded New Project Wizard Templates
- **Delta Lakehouse Analytics Template**: Pre-configured starter project bundling DuckDB, Delta Lake, Polars, and sample time-series data.
- **Modern Data Stack & Airflow Template**: Production pipeline layout with Apache Airflow DAGs, dbt models, and automated data quality checks.

---

## [1.2.0] - 2026-10-11

### ⚡ Smart Delta Auto-Updater Architecture

The **v1.2.0** milestone introduces a bandwidth-efficient in-place delta update mechanism designed to upgrade portable IDE installations with minimal download overhead.

---

### 🌟 Key Feature Highlights

#### 📦 1. Delta Patch Packaging Engine
- **Lightweight Incremental Patches**: Compares releases file-by-file and generates `Jormungandr-Patch-<from>-to-<to>.zip` containing only changed, added, and deleted files.
- **Manifest-Driven Updates**: Includes a cryptographically verified `patch_manifest.json` with SHA-256 checksums, byte sizes, relative file paths, and deletion manifests.
- **Sub-10MB Updates**: Reduces typical release update sizes from ~800MB full IDE downloads down to 5–15MB delta packages.

#### 🛡️ 2. Standalone Portable Updater (`jormungandr-updater.ps1` & `patch_portable_ide.py`)
- **Atomic Staged Patching**: Extracts changes to a temporary staging area and validates all SHA-256 checksums prior to touching the live IDE installation.
- **In-Place File Replacement**: Safely overwrites changed jars, plugins, and launcher executables while preserving user configurations and local projects.
- **Rollback Safeguards**: Backs up modified components before overwriting to ensure clean recovery in case of system interruption.
- **Manifest-Guided Cleanup**: Automatically removes obsolete files and deprecated plugins declared in the deletion manifest.

---

## [1.0.0] - 2026-10-07

### 🎉 Initial Public Release

Welcome to the inaugural release of **Jörmungandr** — the modular, open-source IDE for Python, Data Science, and Analytics built atop the IntelliJ Platform Community Edition.

---

### 🌟 Key Feature Highlights

#### 🪐 1. Reactive Jupyter Notebook Subsystem
- **Native `.ipynb` Editor**: Split code and Markdown cells with syntax highlighting, inline execution, and output virtualization.
- **ZeroMQ v5 Kernel Protocol**: Direct, zero-latency communication with local and remote Python (`ipykernel`) environments.
- **Reactive Dependency DAG & AST Analysis**:
  - Static AST analysis of variables defined and consumed across cells to automatically construct inter-cell directed acyclic execution graphs.
  - Interactive 2D topological graph canvas showing variable lineage and execution order.
  - Automatic `⚠️ Stale` badges indicating cells whose dependencies were modified out of sequence.
  - 1-click Kahn's topological sort execution to eliminate hidden state and restore notebook consistency.
- **JupyterLab Muscle Memory & Command Mode**: Full keyboard navigation (`Esc`, `Enter`, `A`/`B` to insert, `DD` to delete, `M`/`Y` cell type toggles, `J`/`K` navigation, `Shift+Enter` and `Ctrl+Enter` execution).
- **Rich Media & Formula Typography**:
  - LaTeX mathematical typography for `$ ... $`, `$$ ... $$`, and `text/latex` outputs.
  - Interactive collapsible JSON tree inspector with search filtering and path copying.
  - Native audio player card with waveform visualizer and Java Sound playback engine.
  - Native video preview player card with system player launch integration.

#### 📊 2. High-Performance DataFrame Viewer & Data Prep Studio
- **Tabular Grid Explorer**: Virtualized tabular view capable of rendering millions of rows with instant scrolling, column reordering, sorting, and inline filtering (`col:val` queries).
- **Column Analytics**: Per-column distribution histograms, min/mean/max indicators, null count telemetry, and IQR metrics.
- **Automated Data Quality & Correlation Studio**:
  - Multi-metric correlation heatmaps supporting Pearson, Spearman Rank, and Kendall Tau coefficients.
  - Data Quality Scorecard tracking completeness, uniqueness, and column health.
  - Automated IQR/Z-score outlier detection and Pandera schema rule verification.
- **Distribution Drift & Dataset Comparison Studio**:
  - Two-dataset comparison (Train vs Test / Prod vs Staging) with schema diffing.
  - Statistical drift metrics: Population Stability Index (PSI) and Two-Sample Kolmogorov-Smirnov (K-S) test with asymptotic p-value estimation.
  - Severity classification (`🟢 STABLE`, `🟡 MODERATE`, `🔴 SEVERE`) and dual histogram / Empirical CDF overlay visualizer.
- **1-Click Interactive Web App Generator**:
  - Generates standalone, production-ready **Streamlit** and **Gradio** web application scripts directly from any DataFrame or ML model.
  - Generates parameter sliders, categorical filters, metric cards, and Plotly charts.
- **In-Memory DuckDB SQL Lakehouse**: Zero-copy SQL execution directly over active live DataFrames and Parquet files without database servers.
- **Agentic "Talk to Your Data"**: Natural language query engine translating plain English questions into DuckDB SQL queries and Pandas operations.

#### 🗄️ 3. Database & Streaming Suite
- **Relational SQL Support**: Native connection managers and query consoles for PostgreSQL, MySQL, SQLite, DuckDB, Snowflake, and Oracle PL/SQL.
- **NoSQL & Document Engines**: Dedicated studios for MongoDB, Redis (keyspace & command studio), and Apache Cassandra (CQL).
- **Streaming Broker**: Apache Kafka topic inspector, message producer/consumer, and partition lag monitor.
- **Real-Time Streaming & Timeseries Studio**:
  - Sliding memory window buffer (50 to 2000 events) for high-throughput stream ingress without JVM heap bloat.
  - Simulators and live connectors for IoT Sensor Telemetry, FinTech Payments, and Kafka topics.
  - 60 FPS live animated oscilloscope canvas with continuous rolling averages.
  - *"❄️ Freeze to DataFrame"*: 1-click snapshotting of live in-flight streams into native DataFrames.
- **Remote Data Lakes**: Amazon S3 and Google Cloud Storage bucket object explorer.
- **Visual SQL Builder**: Low-code visual join diagram and drag-and-drop query generator.

#### 🧠 4. AI/ML Training, Explainability & Checkpoint Studio
- **Interactive ML Training Studio**:
  - Interactive training for Linear/Ridge Regression, Logistic Classification (with ROC/AUC curves and confusion matrices), K-Means Clustering, and PCA Scree plots.
  - Preliminary feature categorization, distribution analysis, and health profiling on demand without training.
  - Built-in Scikit-Learn, PyTorch, and XGBoost training code templates.
- **Model Explainability & Attribution Studio (SHAP & PDP)**:
  - Local attribution force plots (SHAP Waterfall) visualizing positive/negative feature contributions against base value $E[f(X)]$.
  - Global feature importance ranking bar charts.
  - Partial Dependence Plots (PDP) and Individual Conditional Expectation (ICE) curves.
  - 1-click Python SHAP script generator.
- **Model Checkpoint & Neural Graph Inspector**:
  - Zero-copy header inspection for `.safetensors`, `.onnx`, `.pt`, `.pth`, and `.h5` model files.
  - Tensor shapes, precisions (`F16`, `BF16`, `F32`), and memory footprint analysis.
  - Interactive neural layer feed-forward DAG visualizer.
- **Experiment Tracker & Model Packager**:
  - In-IDE MLflow-style experiment tracking with metric convergence curves.
  - 1-Click production model packager generating FastAPI microservices (`app.py`), Dockerfiles, and client test harnesses.

#### 🔮 5. R Language & Statistical REPL
- Automatic local R binary discovery (`Rscript.exe`, `R.exe`) across system PATH, `R_HOME`, and Conda.
- CRAN package inventorying (`ggplot2`, `dplyr`, `arrow`, `IRkernel`).
- Interactive REPL console with history recall (`Ctrl+Up` / `Ctrl+Down`) and simulation mode fallback.
- Automatic interception of `ggplot2` and base-R graphics into the centralized Scientific Plot Viewer.
- Interception of tabular outputs into the DataFrame Viewer.

#### 🎨 6. Platform Shell & Visual Identity
- Clean IntelliJ Community Edition 2024.3.2 base with Java 21 LTS runtime.
- Bespoke high-resolution branding, custom splash screens, and customized dark/light themes.
- Custom vector iconography (`pluginIcon.svg`) for all modular extension suites.
- Universal interactive 2D pan/zoom controller with mouse-wheel zoom and smooth canvas dragging across all charts and DAGs.
- Deterministic extension lifecycle management with memory limits and zero resource leakage.

#### 🎮 7. Hardware Telemetry & GPU Monitor Status Bar
- **Non-blocking Telemetry Daemon**: Real-time polling of GPU compute utilization, VRAM allocation (used / total MB), and thermals via `nvidia-smi` with automatic Host RAM and CPU fallback.
- **Proactive OOM Prevention Watchdog**: Live status monitoring across `STABLE`, `ELEVATED`, and `CRITICAL_OOM_RISK` tiers to safeguard long-running PyTorch/TensorFlow trainings.
- **Status Bar Widget & Interactive Telemetry Popup**: Click-to-open GPU status bar meter with color-coded gauge bars and thermal readout.
- **1-Click Memory Cache Purge**: Instantly trims JVM off-heap allocations, garbage collection roots, and native cache buffers.

#### 🤖 8. Local AI & LLM Engine (Offline Copilot & Prompt Studio)
- **Local Daemon Integration**: Zero-latency, 100% offline client for local LLM servers (**Ollama** and **vLLM** at `localhost:11434`) ensuring code and data privacy.
- **Model Discovery & Latency Telemetry**: Automatic detection of downloaded weights (`llama3`, `deepseek-coder`, `mistral`, `codellama`, `qwen`) with tokens/second throughput benchmarking.
- **Interactive Prompt Studio Tool Window**: Docked workspace with system prompt customization, temperature tuning slider, and 1-click active editor code insertion.
- **Context-Menu AI Code Actions**: Right-click actions generating Google/NumPy format docstrings and robust PyTest mock fixtures.

#### 🏛️ 9. Modern Lakehouse & Deep Parquet Inspector
- **Binary Parquet Metadata Inspector (`PAR1`)**: Header/footer inspection, row group chunk layouts, compression codec efficiency ratios (Snappy, ZSTD, GZIP, LZ4), dictionary encodings, and column min/max stats.
- **Delta Lake & Apache Iceberg ACID Timeline**: Parses `_delta_log/*.json` and `metadata/*.metadata.json` catalogs to surface snapshot history, commit operations (`WRITE`, `OPTIMIZE`, `MERGE`), and changed file manifests.
- **1-Click Time-Travel SQL Generator**: Produces ready-to-run point-in-time SQL queries for DuckDB and Apache Spark (`VERSION AS OF` / `TIMESTAMP AS OF`).

#### 🔄 10. Data Orchestration & Pipeline Lineage Visualizer (dbt & Airflow)
- **Project AST & Lineage Parser**: Scans projects for dbt SQL models (`ref`, `source`, `config`) and Apache Airflow DAGs (`DAG`, `>>`, `<<`, `task_id`).
- **Interactive 2D DAG Lineage Canvas**: High-performance Swing canvas with anti-aliased cubic bezier connecting arrows, color-coded node badges, and topological layer columns.
- **Graph Interactions**: Cursor-anchored mouse wheel zoom, pan, upstream/downstream dependency highlighting, and double-click jump to source code.
- **Lineage Actions**: 1-click execution simulation and CLI command generator (`dbt run --select`, `airflow tasks test`).

#### 📦 11. Windows Single-File Installer & System Integration
- **Inno Setup Script**: `installer/jormungandr_setup.iss` and `tools/build_installer.ps1` for generating `Jormungandr-Setup-v1.0.0-x64.exe`.
- **System File Associations**: Windows shell registration for `.ipynb` (Jupyter), `.parquet` (Parquet), and `.arrow` / `.feather` (Arrow).
- **Desktop & PATH Integration**: Start Menu and Desktop shortcuts with high-resolution Jörmungandr branding and optional CLI PATH addition.

#### 🔐 12. Commercial Licensing, Anti-Tamper Trial & Licensor Cloud Integration
- **3-Tier Commercial License Model**:
  - `ADM-xxxx-xxxx-xxxx`: Admin Tier (bypasses username/password, collects HWID on launch).
  - `DEV-xxxx-xxxx-xxxx`: Developer Tier (validates credentials and HWID against Licensor cloud).
  - `USER-xxxx-xxxx-xxxx`: User Tier (validates credentials and HWID with concurrency limits).
- **Online Licensor Cloud Backend Integration**:
  - Live API integration with `https://licensor-h5zdysrkqa-uc.a.run.app` for license activation, background heartbeat validation, and deactivation.
- **Bespoke Extension Gating Engine**:
  - Proprietary data platform extensions (Jupyter, DataFrame Viewer, Database Suite, AI Prompt Studio) require a commercial license.
  - Standard IntelliJ Community Edition plugins and external JetBrains Marketplace plugins remain 100% free and open.
- **Persistent Anti-Tamper 60-Day Trial**:
  - Stored in native Windows Registry (`HKCU\Software\Jormungandr\Licensing`) and Java Preferences.
  - Cryptographic HMAC-SHA256 checksums bound to machine HWID prevent trial resetting on uninstall/reinstall.
- **Tiered Visual Badges Across IDE UI**:
  - `USER` (cyan-emerald gradient), `DEV` (electric violet-fuchsia cyberpunk gradient), and `ADMIN` (holographic gold royal gradient with outer glow bloom).
  - Embedded in Window Title Bar, Status Bar, About Dialog, Extensions Manager, and tool window headers.
- **Interactive License Management Modal**:
  - Key masking (`USER-••••-••••-••••`) with Reveal/Hide toggle, 1-click machine HWID copy, online activation form, heartbeat validator, and safe license deactivation.

---

### 🛠️ Build & Verification Infrastructure
- Cross-platform Gradle wrapper build configuration supporting Java 21 LTS.
- `tools/run.ps1` and `tools/run.sh` automated prerequisite verification and environment auto-healing.
- `tools/launch_ide.ps1` direct native launcher bypassing Gradle daemon overhead.
- Automated GitHub Actions release pipeline building both standalone portable distributions and modular plugin packages.
