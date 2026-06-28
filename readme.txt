# what this does

This server implements the Model Context protocol(MCP) - the emerging "USB-C for AI" standard that
lets any AI assistant talk to any tool/data-source.


Claude desktop
chatgpt                    MCP Protocol
cursor                     <---stdio------>       pgvector-mcp-server
Vs code copilot            <----SSE------->       @Tool list_tables(), @Tool query(), @Tool semantic_search()
jetbrains ai                                              |
                                                          |
                                                          postgresql+ pgvector
                                                          (from ask-my-codebase)


Tools exposed

'list_tables'  list all tables in postgress table
'describe_table' show columns, types, and nullability for a table
'query(sql)' execute a read-only sql select query
'semantic_search(query, limit)' find similar code chunks using pgvector embeddings
'count_chunks' count total chunks in vector store
'list_indexed_files' list all source files that have been indexed
'get_file_chunks(file)' get all chunks from a specific source file

This proj is designed to work alongisde ask-my-codebase repo(ingests code, stores embeddings, answers
questions, rest api)
pg-vector-mcp-server-- exposes same database to any mcp client, mcp protocol

same vector store. Two interfaces -- Ingest your code once with ask-my-codebase,
then query it through claude desktop or any MCP-compatible AI


Quick Start
cd codebase
docker compose up -d
docker ps

build server
cd pgvector-mcp-server
./mvnw clean package -DskipTests

configure claude desktop

Mac: '~/Library/Application Support/Claude_desktop_config.json'
Windows: '%APPDATA%\Claude\claude_desktop_config.json'

```json
{
"mcpServers":{
"pgvector":{
"command": "java",
"args":[
"-jar",
"/absolute/path/to/pgvector-mcp-server/target/pgvector-mcp-server-0.1.0.jar"
]
}
}
}


restart claude desktop

Type in claude
List all tables in db
How many code chunks are stored

