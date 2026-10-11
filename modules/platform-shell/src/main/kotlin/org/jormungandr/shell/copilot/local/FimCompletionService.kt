package org.jormungandr.shell.copilot.local

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * High-performance Fill-in-the-Middle (FIM) Code Completion Engine.
 * Interfaces with local Ollama / vLLM server or high-speed offline heuristic completion.
 */
object FimCompletionService {

    private val LOG = Logger.getInstance(FimCompletionService::class.java)
    private val gson = Gson()

    /**
     * Obtains an inline completion proposal for the given code prefix and suffix.
     */
    fun getCompletion(prefix: String, suffix: String, fileExtension: String = "py"): String? {
        val ollama = OllamaService.getInstance()
        if (ollama.isServerReachable()) {
            val models = ollama.listLocalModels()
            val codeModel = models.firstOrNull { it.name.contains("coder", ignoreCase = true) || it.name.contains("code", ignoreCase = true) }
                ?: models.firstOrNull { !it.name.contains("heuristic", ignoreCase = true) }

            if (codeModel != null) {
                val completion = requestOllamaFim(ollama.endpointUrl, codeModel.name, prefix, suffix)
                if (!completion.isNullOrBlank()) {
                    return cleanSuggestion(completion)
                }
            }
        }

        // Fast offline heuristic fallback
        return generateHeuristicCompletion(prefix, fileExtension)
    }

    private fun requestOllamaFim(endpointUrl: String, model: String, prefix: String, suffix: String): String? {
        return try {
            val url = URI("$endpointUrl/api/generate").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 800
                readTimeout = 2500
            }

            // Truncate context window to prevent latency
            val promptPrefix = if (prefix.length > 2000) prefix.takeLast(2000) else prefix
            val promptSuffix = if (suffix.length > 1000) suffix.take(1000) else suffix

            val payload = JsonObject().apply {
                addProperty("model", model)
                addProperty("prompt", promptPrefix)
                addProperty("suffix", promptSuffix)
                addProperty("stream", false)
                val options = JsonObject().apply {
                    addProperty("temperature", 0.1)
                    addProperty("num_predict", 64)
                }
                add("options", options)
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(gson.toJson(payload))
            }

            if (conn.responseCode == 200) {
                val responseJson = conn.inputStream.bufferedReader().use { it.readText() }
                val root = gson.fromJson(responseJson, JsonObject::class.java)
                root.get("response")?.asString
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.debug("Ollama FIM request timed out or failed", e)
            null
        }
    }

    fun generateHeuristicCompletion(prefix: String, fileExtension: String): String? {
        val lastLine = prefix.lines().lastOrNull()?.trimStart() ?: return null
        val tokens = lastLine.split(Regex("\\s+"))

        return when {
            // Python / Pandas / PyTorch completions
            lastLine.startsWith("import ") && !tokens.contains("as") -> {
                when {
                    lastLine == "import numpy" -> " as np"
                    lastLine == "import pandas" -> " as pd"
                    lastLine == "import polars" -> " as pl"
                    lastLine == "import matplotlib.pyplot" -> " as plt"
                    lastLine == "import torch.nn" -> " as nn"
                    lastLine == "import duckdb" -> ""
                    else -> null
                }
            }
            lastLine.startsWith("from ") && !tokens.contains("import") -> {
                when {
                    lastLine == "from torch" -> " import nn, optim"
                    lastLine == "from sklearn.model_selection" -> " import train_test_split"
                    lastLine == "from sklearn.metrics" -> " import accuracy_score, classification_report"
                    lastLine == "from sklearn.ensemble" -> " import RandomForestClassifier"
                    else -> null
                }
            }
            lastLine.endsWith(".groupby(") -> "'category').agg({'value': 'mean'}).reset_index()"
            lastLine.endsWith("pd.read_csv(") -> "'data/dataset.csv')"
            lastLine.endsWith("pl.read_parquet(") || lastLine.endsWith("pd.read_parquet(") -> "'data/dataset.parquet')"
            lastLine.endsWith("torch.tensor(") -> "[[1.0, 0.0], [0.0, 1.0]], dtype=torch.float32)"
            lastLine.endsWith("nn.Linear(") -> "in_features=128, out_features=64)"
            lastLine.startsWith("def ") && !lastLine.contains(":") -> {
                val fnName = lastLine.removePrefix("def ").substringBefore("(").trim()
                if (fnName.isNotEmpty()) "(df: pd.DataFrame) -> pd.DataFrame:" else null
            }
            lastLine.startsWith("class ") && !lastLine.contains(":") -> {
                val clsName = lastLine.removePrefix("class ").substringBefore("(").trim()
                if (clsName.endsWith("Model") || clsName.endsWith("Net")) "(nn.Module):" else ":"
            }
            // SQL completions
            lastLine.uppercase().startsWith("SELECT") && !lastLine.uppercase().contains("FROM") -> {
                " * FROM read_parquet('data/*.parquet') LIMIT 100;"
            }
            lastLine.uppercase().endsWith("GROUP BY") -> " 1, 2 ORDER BY 1 DESC;"
            else -> null
        }
    }

    private fun cleanSuggestion(suggestion: String): String {
        return suggestion
            .removePrefix("```python")
            .removePrefix("```")
            .removeSuffix("```")
            .trimEnd()
    }
}
