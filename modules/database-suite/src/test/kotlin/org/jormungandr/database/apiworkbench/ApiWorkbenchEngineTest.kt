package org.jormungandr.database.apiworkbench

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiWorkbenchEngineTest {

    @AfterEach
    fun tearDown() {
        ApiWorkbenchEngine.stopMockServer()
    }

    @Test
    fun testLiveMockServerLifecycleAndDispatch() {
        val started = ApiWorkbenchEngine.startMockServer(8089)
        assertTrue(started, "Mock server should start successfully on port 8089")
        assertTrue(ApiWorkbenchEngine.isMockServerRunning())

        // Send GET /health
        val req = ApiRequest(
            name = "Health Check",
            protocol = ApiProtocol.REST,
            endpoint = "http://localhost:8089/health",
            method = HttpMethod.GET
        )

        val resp = ApiWorkbenchEngine.execute(req)
        assertEquals(200, resp.statusCode)
        assertTrue(resp.body.contains("jormungandr-inference-mesh"))
        assertFalse(resp.isError)

        // Send POST /v1/models/predict
        val predictReq = ApiRequest(
            name = "Model Predict",
            protocol = ApiProtocol.REST,
            endpoint = "http://localhost:8089/v1/models/predict",
            method = HttpMethod.POST,
            body = "{\"input\": [1, 2, 3]}"
        )

        val predictResp = ApiWorkbenchEngine.execute(predictReq)
        assertEquals(200, predictResp.statusCode)
        assertTrue(predictResp.body.contains("fraud_detection_v2"))
        assertTrue(predictResp.body.contains("predictions"))

        ApiWorkbenchEngine.stopMockServer()
        assertFalse(ApiWorkbenchEngine.isMockServerRunning())
    }

    @Test
    fun testGrpcInferenceSimulation() {
        val grpcReq = ApiRequest(
            name = "Triton Model Infer",
            protocol = ApiProtocol.GRPC,
            endpoint = "localhost:8001",
            grpcService = "inference.GRPCInferenceService",
            grpcMethod = "ModelInfer"
        )

        val resp = ApiWorkbenchEngine.execute(grpcReq)
        assertEquals(200, resp.statusCode)
        assertEquals("GRPC_STATUS_OK", resp.statusText)
        assertTrue(resp.body.contains("resnet50_tensorrt"))
        assertTrue(resp.body.contains("probabilities"))
        assertFalse(resp.isError)
    }

    @Test
    fun testCustomRouteRegistration() {
        val customRoute = MockRoute(
            id = "CUSTOM_1",
            path = "/api/v1/test",
            method = HttpMethod.GET,
            statusCode = 201,
            responseBody = "{\"message\": \"custom route live\"}"
        )

        ApiWorkbenchEngine.addMockRoute(customRoute)
        assertTrue(ApiWorkbenchEngine.getRegisteredRoutes().any { it.path == "/api/v1/test" })

        ApiWorkbenchEngine.removeMockRoute("/api/v1/test")
        assertFalse(ApiWorkbenchEngine.getRegisteredRoutes().any { it.path == "/api/v1/test" })
    }

    @Test
    fun testTemplatesAvailability() {
        val templates = ApiWorkbenchEngine.getRequestTemplates()
        assertTrue(templates.containsKey("Triton v2 Model Infer (REST)"))
        assertTrue(templates.containsKey("vLLM Chat Completion (REST)"))
        assertTrue(templates.containsKey("GraphQL Model Registry Query"))
        assertTrue(templates.containsKey("Triton gRPC TensorRT Stream"))
    }
}
