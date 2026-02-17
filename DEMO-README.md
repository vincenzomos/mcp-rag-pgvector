# MCP Demo Services

This project demonstrates two different approaches to data retrieval using MCP (Model Context Protocol) services:

## 🕒 **Timesheet Service** (Relational Database)
- **Service**: `RelationalHoursMcpService`
- **Data Source**: PostgreSQL database with structured timesheet data
- **Use Case**: Precise queries for hours, projects, and time periods
- **Example**: "How many hours for Devops Client Reporting did I book in October 2021?" → **160 hours**

### Key Features:
- ✅ Exact temporal matching (finds October 2021 precisely)
- ✅ Structured JSON responses (no parsing errors)
- ✅ Fast SQL queries with indexes
- ✅ Comprehensive search with filters

### Available MCP Tools:
- `searchTimesheetsDB` - Search timesheet data with natural language
- `getTimesheetStatistics` - Get comprehensive statistics
- `getProjectInformation` - Detailed project analysis

## 👤 **CV Service** (Vector RAG)
- **Service**: `CVMcpService`  
- **Data Source**: Vector embeddings of CV documents
- **Use Case**: Semantic search for skills, experience, qualifications
- **Example**: "Find Java Spring Boot experience" → Returns relevant CV sections

### Key Features:
- ✅ Semantic similarity search
- ✅ Context-aware content retrieval
- ✅ Skills extraction and analysis
- ✅ Candidate comparison capabilities

### Available MCP Tools:
- `searchCVInformation` - Search CV content semantically
- `generateCVSummary` - Generate comprehensive candidate profiles

## 🚀 **Demo Usage**

### Using MCP Client
Both services are automatically registered as MCP tools when the application starts.

### Using REST API (for testing)
```bash
# Test timesheet search
curl "http://localhost:8080/api/demo/hours/search?query=October 2021 Devops"

# Get timesheet statistics  
curl "http://localhost:8080/api/demo/hours/statistics"

# Search CV information
curl "http://localhost:8080/api/demo/cv/search?query=Java Spring"

# Generate CV summary
curl "http://localhost:8080/api/demo/cv/summary"
```

### Browse Demo Interface
Visit: http://localhost:8080/api/demo/

## 🗃️ **Data Sources**

### Timesheet Data
- **File**: `src/main/resources/hoursheets/Hoursheets.csv`
- **Coverage**: 2020-2025, 84+ records
- **Projects**: Devops ClientReporting, Standby ClientReporting
- **Storage**: PostgreSQL `timesheets` table

### CV Data  
- **File**: `src/main/resources/cv/BEREND-BOTJE-CV.pdf`
- **Storage**: Vector embeddings in `vector_store` table
- **Metadata**: Skills, experience level, education, chunk types

## 🔧 **Setup**

1. **Start Database**:
   ```bash
   ./reset-database.bat
   ```

2. **Start Application**:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Verify Data Loading**:
   - Watch logs for "Successfully ingested X timesheet records"
   - Watch logs for "Successfully ingested X CV chunks"

## 📊 **Architecture Comparison**

| Aspect | Timesheet Service | CV Service |
|--------|------------------|------------|
| **Data Type** | Structured (CSV) | Unstructured (PDF) |
| **Search Method** | SQL queries | Vector similarity |
| **Precision** | Exact matches | Semantic similarity |
| **Use Case** | Factual queries | Content discovery |
| **Performance** | Very fast | Good (depends on embeddings) |
| **Flexibility** | Schema dependent | Highly flexible |

This demonstrates the power of choosing the right tool for the right job: relational databases for structured data with exact queries, and vector RAG for unstructured content with semantic search needs.
