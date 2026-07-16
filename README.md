# CodeSense AI

A DSA practice platform that visualizes Java and Python code execution step-by-step with AI analysis.

**Live demo:** [codesense-ai-phi.vercel.app](https://codesense-ai-phi.vercel.app)

[![Deploy](https://github.com/Anujpal-15th/codesense-ai/actions/workflows/deploy.yml/badge.svg)](https://github.com/Anujpal-15th/codesense-ai/actions/workflows/deploy.yml)

## What it does

Paste a Java or Python function and CodeSense AI does two things in parallel:

- **Analyzes it** — names the underlying algorithmic pattern, estimates time and space complexity in Big-O notation, and gives a straight verdict on whether the approach is optimal, plus style/readability feedback and suggestions.
- **Runs it** — actually compiles and executes the code in a sandboxed environment, capturing a real step-by-step execution trace (variables, call stack, console output) that you can scrub through line by line in a Monaco-based visualizer.

No fabricated traces: the visualizer is driven entirely by real captured execution data, not an LLM guessing at control flow.

## Features

- Real step-by-step code execution visualization — [JDI](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdi/) (Java Debug Interface) for Java, `sys.settrace` for Python
- AI pattern detection (Sliding Window, Two Pointers, Dynamic Programming, Binary Search, DFS/BFS, Hash Map, and more)
- Time and space complexity analysis with an optimality verdict
- Docker-sandboxed execution for security
- Support for Java and Python

## Tech stack

| | |
|---|---|
| **Frontend** | React 19, Vite, Tailwind CSS, Monaco Editor |
| **Backend** | Java 17, Spring Boot 3.3.4, Maven |
| **Database** | PostgreSQL (Neon) |
| **Execution** | Docker, JDI (Java), `sys.settrace` (Python) |
| **AI** | GitHub Models (gpt-4o-mini) |
| **Deployment** | Vercel (frontend), Azure VM (backend), GitHub Actions CI/CD |

## Monorepo layout

```
codesense-ai/
├── backend/    Spring Boot REST API (Java 17, Maven)
└── frontend/   React client (Vite)
```

## Running locally

### Prerequisites

- Java 17+ and Maven
- Node 20+
- PostgreSQL, reachable and with a database/role created for the app
- A [GitHub Personal Access Token](https://github.com/settings/tokens) with the `models: read` scope (default LLM provider — see below for alternatives)

### Backend

```bash
cd backend
export DB_USERNAME=<your-postgres-user>
export DB_PASSWORD=<your-postgres-password>
export GITHUB_MODELS_TOKEN=<your-github-pat>
mvn spring-boot:run
```

Runs on `http://localhost:8080`. The schema is created automatically on first startup (Hibernate `ddl-auto: update`).

Other LLM providers (Anthropic, Gemini, or a local Ollama model) are supported via `LLM_PROVIDER` and the matching API key — see `backend/src/main/resources/application.yml` for every configurable option (execution sandbox mode, trace limits, rate limiting, etc.).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and expects the backend at `http://localhost:8080` by default (override with `VITE_API_BASE_URL`).

## Screenshots

_(placeholder — add screenshots of the Workspace, Analysis, and Visualize views here)_

## Author

**Anuj Pal** — [@Anujpal-15th](https://github.com/Anujpal-15th)

For a deeper walkthrough of the architecture, execution engine, design decisions, and how
to extend or deploy the project, see [memory.md](memory.md).
