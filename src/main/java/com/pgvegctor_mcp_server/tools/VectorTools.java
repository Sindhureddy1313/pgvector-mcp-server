package com.pgvegctor_mcp_server.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorTools {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbc;

    @Tool(description = "Count the total number of chunks stored in the vector store. Useful for checking if data has been ingested.")
    public ChunkCount countChunks(){
        log. info ("MCP Tool: count_chunks called");

    // Check if table exists first
    String checkSql = """
            SELECT EXISTS (
            SELECT FROM information_schema.tables
            WHERE table_schema = 'public" AND table_name = "vector_store'
            )
            """;

    Boolean exists = jdbc.queryForObject(checkSql, Boolean.class);
    if (!Boolean. TRUE. equals(exists)) {
        return new ChunkCount(0L, 0L, "vector_store table does not exist. Run ingest via ask-my-codebase first.");
    }
    String countSql = "SELECT COUNT (*) FROM vector_store";
    Long total = jdbc.queryForObject(countSql, Long.class);

    String sourcesSql = "SELECT COUNT (DISTINCT metadata->>'source') FROM vector_store";
    Long sources= jdbc.queryForObject(sourcesSql, Long.class);

    return new ChunkCount(total != null? total: 0, sources != null ? sources: 0, null);
    }

    @Tool(description = """
    Perform semantic search over code chunks using vector similarity.
    Finds chunks whose meaning is similar to the query text, even if exact words don't match.
    Uses the same embeddings and data from the ask-my-codebase project.
    """)
    public SemanticSearchResult semanticSearch(
    @ToolParam(description = "Natural Language query to search for (e.g., 'how to validate email' or 'database connection handling')") String query,
    @ToolParam(description = "Maximum number of results to return (1-20, default 5)") Integer limit) {
        log.info("MCP Tool: semantic_search called with query: '(', limit: {l", query, limit);

        // Validate limit
        int maxesults = (limit == null || limit < 1) ? 5 : Math.min(limit, 20);
        // Check if table exists
        String checkSql = """
        SELECT EXISTS (
                SELECT FROM information_schema. tables
        WHERE table_schema = 'public' AND table_name = 'vector_store'
        )
        """;

        Boolean exists = jdbc.queryForObject(checkSql, Boolean.class);
        if (!Boolean. TRUE. equals(exists)) {
            return SemanticSearchResult.error("vector_store table does not exist. Run ingest via ask-my-codebase first.");
        }
            try {
                // Generate embedding for the query using Ollama (nomic-embed-text)
                float[] embedding = embeddingModel.embed(query) ;
                // Build the pector query with cosine distance
                String searchSql= """
                SELECT
                        id,
                        LEFT (content,500) AS content_preview,
                        metadata->>'source' AS source file,
                        (metadata->>'chunk_index')::int AS chunk_index,
                        1 - (embedding <=> ?::vector) AS similarity_score
                FROM vector_store
                ORDER BY embedding <=> ?:: vector
                LIMIT ?
                """;

                // Convert float] to pavector format string
                String vectorStr = floatArrayToVectorString(embedding);

                List<Map<String, Object>> results= jdbc.queryForList(searchSql, vectorStr, vectorStr, maxesults);
                return SemanticSearchResult. success(results, results.size(), query);
            }
            catch (Exception e) {
                log.error("Semantic search failed: {)", e.getMessage(), e);
                return SemanticSearchResult.error("Semantic search failed: " + e.getMessage());
            }
    }



    @Tool(description = "List all unique source files that have been indexed in the vector store.")
    public List<String> listIndexedFiles() {

        log.info ("MCP Tool: list_indexed_files called");
        String sql = """
                    SELECT DISTINCT metadata->>'source' AS source_file
                    FROM vector_store
                    WHERE metadata->>'source' IS NOT NULL
                    ORDER BY source_file """;

        return jdbc.queryForList(sql, String.class);

    }

    @Tool(description = "Get all chunks from a specific source file.")
    public FileChunksResult getFileChunks(
    @ToolParam(description = "Path of the source file (e.g., 'com/sindhu/service/RagService.java') ")
    String sourceFile) {
        log. info ("MCP Tool: get_file_chunks called for: {)", sourceFile);
        String sql = """
        SELECT
        LEFT (content, 300) AS content_preview, (metadata->>'chunk_index')::int AS chunk_index
        FROM vector_store
        WHERE metadata->>'source'=?
                ORDER BY (metadata->>'chunk_index'):: int
                """;

        List<Map<String, Object>> chunks = jdbc.queryForList(sql, sourceFile);
        if (chunks.isEmpty()) {
            return new FileChunksResult(sourceFile, 0, List.of(),
                    "No chunks found for file: " + sourceFile + ". Check the exact path with list_indexed_files.");
        }
        return new FileChunksResult(sourceFile, chunks. size(), chunks, null);
    }



    private String floatArrayToVectorString(float[] embedding) {
            StringBuilder sb = new StringBuilder("l");
            for (int i = 0; i < embedding.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(embedding[i]);
            }
                sb. append("]");
                return sb.toString();
            }

// --- Result records ---
      public record ChunkCount (Long totalChunks, Long uniqueSourceFiles, String warning){}

      public record SemanticSearchResult(
                boolean success,
                String error,
                String query,
                int resultcount,
                List<Map<String, Object>> results){

                public static SemanticSearchResult success(List<Map<String, Object>> results, int count, String query) {
                    return new SemanticSearchResult(true, null, query, count, results);
                }
                public static SemanticSearchResult error (String message){
                    return new SemanticSearchResult(false, message, null, 0, List.of());
                }
      }

    public record FileChunksResult(String sourceFile, int chunkCount, List<Map<String, Object>>chunks, String warning) {}

}
