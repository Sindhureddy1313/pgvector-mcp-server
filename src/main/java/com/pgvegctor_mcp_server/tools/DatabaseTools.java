package com.pgvegctor_mcp_server.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseTools {

    private final JdbcTemplate jdbc;

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "ALTER", "CREATE", "TRUNCATE","CALL", "EXECUTE",
            "EXEC","REVOKE","GRANT","DROP",";"
    );

    @Tool(description = "List all tables in the PostgreSQL database. Returns table names and their schemas")
    public List<Map<String , Object>> listTables(){
        log.info("MCP Tool: list_tables called");
        String sql = """
                SELECT table_schema, table_name, table_type
                FROM information_schema.tables
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY table_schema, table_name
                """;
        return jdbc.queryForList(sql);
    }

    @Tool(description = "Describe the columns of a specified table. Returns column names, data types and nullability.")
    public List<Map<String, Object>> describeTable(
            @ToolParam(description = "Name of the table to describe(e.g., 'vector_store' or 'public.vector_store')") String tableName){
        log.info("MCP Tool: describe_table called for '{}' ", tableName);

        String schema = "public";
        String table = tableName;
        if(tableName.contains(".")){
            String[] parts = tableName.split("\\.", 2);
            schema = parts[0];
            table = parts[1];
        }

        String sql = """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? and table_name = ?
                ORDER BY ordinal_position
                """;
        return jdbc.queryForList(sql,schema,tableName);
    }


    @Tool(description = """
            Execute a read-only SQL SELECT query against the database.
            Returns the query result as a list of rows.
            Only SELECT statements are allowed for safety.
            Limit results to avoid overwhelming output ( add LIMIT clause if needed).
            """)
    public QueryResult query(
            @ToolParam(description = "SQL SELECT query to execute. Must be a SELECT statament only.") String sql){

        log.info("MCP Tool: query called with SQL: {}", sql);

        String upper = sql.toUpperCase().trim();
        if(!upper.startsWith("SELECT")){
            return QueryResult.error("Only select statements are allowed. Your query must start with SELECT");
        }

        for(String forbidden: FORBIDDEN_KEYWORDS){
            if(forbidden.equals("SELECT")) continue;
            if(upper.contains(forbidden)){
                return QueryResult.error("Forbidden keyword detected: " + forbidden + ". Only read-only SELECT queries are allowed.");
            }
        }

        if(sql.contains(";") && !sql.trim().endsWith(";")){
            return QueryResult.error("Multiple statements detected.Only read-only SELECT queries are allowed. ");
        }

        try {
            List<Map<String,Object>> rows = jdbc.queryForList(sql.replace(";", ""));
            return QueryResult.success(rows, rows.size());
        } catch (Exception e){
            log.error("Query failed: {}", e.getMessage());
            return QueryResult.error("Query failed: " + e.getMessage());
        }
    }

    public record QueryResult(boolean success, String error, int rowCount, List<Map<String, Object>> rows){
        public static  QueryResult success(List<Map<String, Object>> rows, int count){
            return new QueryResult(true, null, count, rows);
        }

        public static QueryResult error(String message){
            return new QueryResult(false, message, 0, List.of());
        }
    }
}
