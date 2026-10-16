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

package org.jormungandr.dataframe.featurestore

enum class FeatureStoreProvider(val displayName: String) {
    FEAST("Feast (Open-Source)"),
    HOPSWORKS("Hopsworks Feature Store"),
    DELTA_LAKEHOUSE("Delta Lakehouse Time-Travel"),
    IN_MEMORY("Jörmungandr In-Memory Store")
}

data class FeatureEntity(
    val name: String,
    val joinKey: String,
    val description: String = ""
)

data class FeatureField(
    val name: String,
    val dataType: String,
    val description: String = ""
)

data class FeatureViewDefinition(
    val name: String,
    val entities: List<String>,
    val features: List<FeatureField>,
    val ttlSeconds: Long = 86400 * 30L, // 30 days
    val isOnlineEnabled: Boolean = true,
    val sourceTable: String = ""
)

data class TimeTravelSnapshot(
    val version: Long,
    val timestampMs: Long,
    val rowCount: Int,
    val changeType: String,
    val commitMessage: String
)

data class PointInTimeJoinConfig(
    val entityJoinKey: String,
    val timestampColumn: String,
    val selectedFeatures: List<String>
)
