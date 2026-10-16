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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Core engine for API testing, ML inference microservice dispatch,
 * and live embedded HTTP/REST mock server orchestration.
 */
object ApiWorkbenchEngine {

    private val mockRoutes = ConcurrentHashMap<String, MockRoute>()
    private var httpServer: HttpServer? = null
    private var mockPort: Int = 8089

    init {
        // Seed default microservice inference mock routes
        registerDefaultMockRoutes()
    }

    fun isMockServerRunning(): Boolean = httpServer != null

    fun getMockPort(): Int = mockPort

    fun getRegisteredRoutes(): List<MockRoute> = mockRoutes.values.toList()

    fun addMockRoute(route: MockRoute) {
        mockRoutes[route.path] = route
    }

    fun removeMockRoute(path: String) {
        mockRoutes.remove(path)
    }

    fun startMockServer(port: Int = 8089): Boolean {
        if (httpServer != null) return true
        return try {
            mockPort = port
            val server = HttpServer.create(InetSocketAddress(port), 0)
            server.createContext("/") { exchange ->
                handleMockExchange(exchange)
            }
            server.executor = Executors.newFixedThreadPool(4)
            server.start()
            httpServer = server
            true
        } catch (e: Exception) {
            httpServer = null
            false
        }
    }

    fun stopMockServer() {
        httpServer?.stop(0)
        httpServer = null
    }

    private fun handleMockExchange(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val route = mockRoutes[path] ?: mockRoutes["/"]

        if (route != null) {
            if (route.simulatedDelayMs > 0) {
                Thread.sleep(route.simulatedDelayMs)
            }
            val responseBytes = route.responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", route.contentType)
            exchange.responseHeaders.set("Server", "Jormungandr-Mock-Server/1.0")
            exchange.sendResponseHeaders(route.statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { os ->
                os.write(responseBytes)
            }
        } else {
            val notFound = "{\"error\": \"Route not found on mock server\", \"path\": \"$path\"}".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(404, notFound.size.toLong())
            exchange.responseBody.use { it.write(notFound) }
        }
    }

    private fun registerDefaultMockRoutes() {
        mockRoutes["/health"] = MockRoute(
            id = "HEALTH",
            path = "/health",
            method = HttpMethod.GET,
            statusCode = 200,
            responseBody = "{\"status\": \"UP\", \"service\": \"jormungandr-inference-mesh\", \"version\": \"1.5.0\"}"
        )
        mockRoutes["/v1/models/predict"] = MockRoute(
            id = "PREDICT",
            path = "/v1/models/predict",
            method = HttpMethod.POST,
            statusCode = 200,
            responseBody = "{\n  \"model_name\": \"fraud_detection_v2\",\n  \"predictions\": [0.012, 0.988],\n  \"inference_latency_ms\": 14.2,\n  \"decision\": \"FLAG_TRANSACTION\"\n}"
        )
        mockRoutes["/graphql"] = MockRoute(
            id = "GRAPHQL",
            path = "/graphql",
            method = HttpMethod.POST,
            statusCode = 200,
            responseBody = "{\n  \"data\": {\n    \"registeredModels\": [\n      {\"name\": \"xgboost_churn\", \"status\": \"ACTIVE\", \"replicas\": 3},\n      {\"name\": \"vit_embeddings\", \"status\": \"WARMING\", \"replicas\": 1}\n    ]\n  }\n}"
        )
    }

    /**
     * Executes an API request (REST, GraphQL, or gRPC) against a live endpoint or mock fallback.
     */
    fun execute(request: ApiRequest): ApiResponse {
        val startMs = System.currentTimeMillis()

        if (request.protocol == ApiProtocol.GRPC) {
            return simulateGrpcInvocation(request, startMs)
        }

        return try {
            val url = URI.create(request.endpoint).toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = request.method.name
                connectTimeout = 4000
                readTimeout = 6000
                doInput = true

                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }

                if (request.method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH) && request.body.isNotEmpty()) {
                    doOutput = true
                    val outBytes = request.body.toByteArray(StandardCharsets.UTF_8)
                    if (getRequestProperty("Content-Type") == null) {
                        setRequestProperty("Content-Type", if (request.protocol == ApiProtocol.GRAPHQL) "application/json" else "application/json")
                    }
                    outputStream.use { it.write(outBytes) }
                }
            }

            val code = conn.responseCode
            val text = conn.responseMessage ?: "OK"
            val inStream = if (code >= 400) conn.errorStream ?: conn.inputStream else conn.inputStream
            val body = inStream.bufferedReader().use { it.readText() }
            val latency = System.currentTimeMillis() - startMs

            val headers = mutableMapOf<String, String>()
            conn.headerFields.forEach { (k, v) ->
                if (k != null) headers[k] = v.joinToString(", ")
            }

            ApiResponse(
                statusCode = code,
                statusText = text,
                headers = headers,
                body = body,
                latencyMs = latency,
                byteSize = body.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                isError = code >= 400
            )
        } catch (e: Exception) {
            // High-fidelity fallback / mock simulation
            simulateResponse(request, startMs, e.message ?: "Connection Refused")
        }
    }

    private fun simulateGrpcInvocation(request: ApiRequest, startMs: Long): ApiResponse {
        val latency = (Math.random() * 25 + 10).toLong()
        val service = request.grpcService ?: "inference.GRPCInferenceService"
        val method = request.grpcMethod ?: "ModelInfer"

        val body = """
        {
          "model_name": "resnet50_tensorrt",
          "model_version": "1",
          "id": "req_849204",
          "parameters": {
            "sequence_id": 1,
            "sequence_start": false
          },
          "outputs": [
            {
              "name": "probabilities",
              "datatype": "FP32",
              "shape": [1, 1000],
              "data": [0.892, 0.045, 0.012, 0.003, "..."]
            }
          ],
          "grpc_metadata": {
            "service": "$service",
            "method": "$method",
            "status": "OK (0)"
          }
        }
        """.trimIndent()

        return ApiResponse(
            statusCode = 200,
            statusText = "GRPC_STATUS_OK",
            headers = mapOf("content-type" to "application/grpc+proto", "grpc-status" to "0"),
            body = body,
            latencyMs = latency,
            byteSize = body.length.toLong(),
            isError = false
        )
    }

    private fun simulateResponse(request: ApiRequest, startMs: Long, errorMsg: String): ApiResponse {
        val latency = maxOf(System.currentTimeMillis() - startMs, 15L)
        return if (request.endpoint.contains("8089") || request.endpoint.contains("mock")) {
            val route = mockRoutes[URI.create(request.endpoint).path] ?: mockRoutes["/health"]
            val body = route?.responseBody ?: "{\"mock\": true, \"status\": \"simulated\"}"
            ApiResponse(
                statusCode = 200,
                statusText = "OK (Simulated Mock)",
                headers = mapOf("Content-Type" to "application/json", "X-Jormungandr-Mock" to "True"),
                body = body,
                latencyMs = latency,
                byteSize = body.length.toLong(),
                isError = false
            )
        } else {
            ApiResponse(
                statusCode = 503,
                statusText = "Service Unavailable / Simulated Fallback",
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\n  \"error\": \"Target endpoint unavailable: $errorMsg\",\n  \"tip\": \"Start the live Mock Server on port 8089 to test locally.\"\n}",
                latencyMs = latency,
                byteSize = 120L,
                isError = true
            )
        }
    }

    fun getRequestTemplates(): Map<String, ApiRequest> = mapOf(
        "Triton v2 Model Infer (REST)" to ApiRequest(
            name = "Triton Model Infer",
            protocol = ApiProtocol.REST,
            endpoint = "http://localhost:8089/v1/models/predict",
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\n  \"inputs\": [\n    {\"name\": \"data\", \"shape\": [1, 3, 224, 224], \"datatype\": \"FP32\"}\n  ]\n}"
        ),
        "vLLM Chat Completion (REST)" to ApiRequest(
            name = "vLLM Chat",
            protocol = ApiProtocol.REST,
            endpoint = "http://localhost:8089/v1/models/predict",
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\n  \"model\": \"mistralai/Mistral-7B-Instruct-v0.2\",\n  \"messages\": [{\"role\": \"user\", \"content\": \"Predict churn risk for customer 42\"}]\n}"
        ),
        "GraphQL Model Registry Query" to ApiRequest(
            name = "Model Registry Query",
            protocol = ApiProtocol.GRAPHQL,
            endpoint = "http://localhost:8089/graphql",
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\n  \"query\": \"query { registeredModels { name status replicas } }\"\n}"
        ),
        "Triton gRPC TensorRT Stream" to ApiRequest(
            name = "Triton gRPC Infer",
            protocol = ApiProtocol.GRPC,
            endpoint = "localhost:8001",
            grpcService = "inference.GRPCInferenceService",
            grpcMethod = "ModelInfer",
            body = "{\n  \"model_name\": \"resnet50_tensorrt\",\n  \"batch_size\": 1\n}"
        )
    )
}
