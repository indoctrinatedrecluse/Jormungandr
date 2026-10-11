# 🐍 Jörmungandr v1.1.0 🌊

*The Modular, Open-Source IDE for Python, Data Science, Analytics & Local AI*

---

## 🌟 What's New in v1.1.0

### 1. 🗄️ Lakehouse Studio: In-Place Time-Travel Query Execution
- Added **"▶ Run in DuckDB"** action in Lakehouse Studio.
- Execute SQL time-travel queries directly against Delta Lake, Apache Iceberg, and Apache Hudi snapshots.
- Interactive query result modal with full tabular preview and **"📊 Stream to DataFrame Studio"** integration.

### 2. ⚡ Pipeline Studio: Live Task Telemetry & Execution Simulation
- Added interactive topological execution simulation (**"▶️ Simulate Run"** and **"🔄 Reset"**).
- Dynamic visual states (`QUEUED`, `RUNNING`, `SUCCESS`) across dependency layers.
- Animated electric-blue pulsing glow effect on currently executing lineage nodes on the 2D canvas.

### 3. 🤖 Local AI Prompt Studio: 1-Click Model Pulling
- Embedded model pulling engine connected directly to Ollama's `POST /api/pull` streaming API.
- Live download progress bar with percentage, downloaded byte counts, and phase status (`pulling manifest`, `downloading`, `verifying sha256`).
- Automatic refresh of the local model selector upon download completion.

### 4. 📈 Hardware Telemetry: 60-Second Sparkline & Experiment Profiling
- Real-time dual-curve strip chart canvas in GPU Monitor dialog (cyan compute utilization, fuchsia VRAM occupancy) with a rolling 60-second historical window.
- One-click **"📋 Export Telemetry CSV"** action.
- Automatic hardware snapshot logging to ML Experiment Tracker and MLflow runs.

### 5. 💿 Turnkey Windows Installer (`.exe`)
- Single-file native Windows setup wizard compiled using Inno Setup (`Jormungandr-Setup-v1.1.0-x64.exe`).
- Complete Start Menu and Desktop shortcuts.
- Native Windows shell file associations for Jupyter Notebooks (`.ipynb`), Apache Parquet (`.parquet`), and Apache Arrow/Feather (`.arrow`, `.feather`).

### 6. 📌 Reactive Notebooks: Pin Output & Split View
- Added **"📌 Pin Output to Side Panel"** to cell actions menu.
- Dedicated `📌 Pinned Output` side panel tab for keeping charts, dataframes, and logs docked side-by-side while editing other cells.
- Real-time reactive updates as pinned cells are re-executed.

### 7. 🛡️ IDE Lifecycle & Shutdown Hardening
- Resolved application hang during exit in the "Saving Settings" dialog.
- Enforced non-blocking session disposal, socket receive/send timeouts (500ms), linger=0, and clean background process termination.

---

## 📦 Release Artifacts

| Distribution Package | Description | Size |
| :--- | :--- | :--- |
| **`Jormungandr-Setup-v1.1.0-x64.exe`** | **Turnkey Windows Installer** (Recommended) | ~951 MB |
| **`Jormungandr-v1.1.0-windows-x64.zip`** | Standalone Portable IDE (Zero install required) | ~1.27 GB |
| **`Jormungandr-Plugin-v1.1.0.zip`** | Modular Extension Bundle for IntelliJ / PyCharm | ~77 MB |
