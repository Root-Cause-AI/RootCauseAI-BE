package com.example.incidentinvestigator.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Inspection and discovery endpoint for the RootCause AI MCP server.
 *
 * <p>Provides tool metadata, schemas, and transport details for
 * MCP clients, MCP Inspector, and administrative auditing.</p>
 */
@RestController
@RequestMapping("/api/mcp")
public class McpInfoController {

    private static final Logger log = LoggerFactory.getLogger(McpInfoController.class);

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getMcpServerInfo() {
        log.info("GET /api/mcp/info - Serving MCP server discovery metadata");

        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "name", "searchKnowledgeBase",
                        "description", "Searches the vector knowledge base for runbooks and known fixes relevant to an error or symptom.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "Technical query or error pattern to search")
                                ),
                                "required", List.of("query")
                        )
                ),
                Map.of(
                        "name", "findSimilarIncidents",
                        "description", "Searches for past incidents that share symptoms with the current incident.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "incidentDescription", Map.of("type", "string", "description", "Description of incident symptoms")
                                ),
                                "required", List.of("incidentDescription")
                        )
                ),
                Map.of(
                        "name", "analyzeLogPatterns",
                        "description", "Extracts recurring stack traces, SQL exceptions, connection timeouts, and component signatures from logs.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "logs", Map.of("type", "string", "description", "Raw application log text")
                                ),
                                "required", List.of("logs")
                        )
                ),
                Map.of(
                        "name", "getDeploymentContext",
                        "description", "Parses deployment metadata, commit hashes, image tags, and environment diffs.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "deploymentInfo", Map.of("type", "string", "description", "Raw deployment info or YAML")
                                ),
                                "required", List.of("deploymentInfo")
                        )
                )
        );

        Map<String, Object> response = Map.of(
                "serverName", "rootcause-ai-investigator",
                "version", "1.0.0",
                "protocolVersion", "2024-11-05",
                "transport", "WebMVC HTTP / SSE",
                "toolsCount", tools.size(),
                "tools", tools
        );

        return ResponseEntity.ok(response);
    }
}
