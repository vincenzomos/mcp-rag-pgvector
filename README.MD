# mcp-rag-pgvector

MCP server app that presents MCP tools and prompts for a asimple RAG implementation

2 tools :
- getProjectCodes : returns a list of project codes to write on.
- searchUren : a tool to query the uren database for relevant info

Using Postgress pgvector extension for vector search and retrieval.

### starting the Postgres database
docker-compose -f docker-compose-postgres.yaml up