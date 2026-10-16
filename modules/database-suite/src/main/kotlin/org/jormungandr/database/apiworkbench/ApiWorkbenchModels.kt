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

package org.jormungandr.database.apiworkbench

enum class ApiProtocol(val displayName: String) {
    REST("REST / HTTP"),
    GRAPHQL("GraphQL"),
    GRPC("gRPC / Protobuf")
}

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH
}

data class ApiRequest(
    val name: String,
    val protocol: ApiProtocol = ApiProtocol.REST,
    val endpoint: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val grpcService: String? = null,
    val grpcMethod: String? = null
)

data class ApiResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
    val latencyMs: Long,
    val byteSize: Long,
    val isError: Boolean = statusCode >= 400
)

data class MockRoute(
    val id: String,
    val path: String,
    val method: HttpMethod = HttpMethod.GET,
    val statusCode: Int = 200,
    val contentType: String = "application/json",
    val responseBody: String = "{\"status\": \"ok\"}",
    val simulatedDelayMs: Long = 0L
)
