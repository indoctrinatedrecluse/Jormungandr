/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.shell.llm.eval

enum class EvalMetricType(val displayName: String) {
    ROUGE_L("ROUGE-L"),
    BLEU_SCORE("BLEU"),
    EXACT_MATCH("Exact Match"),
    SEMANTIC_SIMILARITY("Semantic Sim"),
    HALLUCINATION_SCORE("Hallucination"),
    TOXICITY_SCORE("Toxicity"),
    LATENCY_MS("Latency (ms)"),
    TOKEN_EFFICIENCY("Token/Sec")
}

enum class RedTeamAttackVector(val displayName: String, val category: String) {
    JAILBREAK_DAN("DAN 11.0 / Jailbreak", "Policy Bypass"),
    PROMPT_INJECTION("Direct Prompt Injection", "Command Hijack"),
    SYSTEM_LEAKAGE("System Prompt Leakage", "Exfiltration"),
    ADVERSARIAL_SUFFIX("Adversarial Suffix Attack", "Adversarial Robustness"),
    ROLEPLAY_OVERRIDE("Malicious Roleplay Persona", "Safety Bypass"),
    PII_EXTRACTION("Targeted PII Probe", "Data Privacy")
}

data class EvalTestCase(
    val id: String,
    val prompt: String,
    val expectedOutput: String? = null,
    val attackVector: RedTeamAttackVector? = null,
    val tags: List<String> = emptyList()
)

data class EvalTestResult(
    val testCase: EvalTestCase,
    val modelName: String,
    val actualOutput: String,
    val metrics: Map<EvalMetricType, Double>,
    val passed: Boolean,
    val latencyMs: Long,
    val vulnerabilityDetected: Boolean = false,
    val defenseNote: String = ""
)

data class EvalBenchmarkReport(
    val suiteName: String,
    val modelTarget: String,
    val results: List<EvalTestResult>,
    val overallScore: Double,
    val redTeamDefenseRate: Double,
    val averageLatencyMs: Long,
    val generatedAtMs: Long = System.currentTimeMillis()
)
