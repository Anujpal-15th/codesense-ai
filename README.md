# CodeSense AI

[![Deploy](https://github.com/Anujpal-15th/codesense-ai/actions/workflows/deploy.yml/badge.svg)](https://github.com/Anujpal-15th/codesense-ai/actions/workflows/deploy.yml)

CodeSense AI is a tool for analyzing data structures & algorithms code: paste a
snippet, and it identifies the underlying pattern, estimates time/space
complexity, and reports whether the approach is optimal. It's aimed at
developers preparing for technical interviews who want fast, structured
feedback on their solutions instead of manually reasoning through complexity
analysis.

## Monorepo layout

```
codesense-ai/
├── backend/    Spring Boot REST API (Java 17, Maven)
└── frontend/   React client (Vite)
```

### [backend/](backend)
Spring Boot service exposing the REST API. Handles auth, persistence
(PostgreSQL), caching (Redis), and code analysis.

### [frontend/](frontend)
React single-page app where users paste code and view analysis results, with
a Monaco-based code editor.

## Status

This is a fresh scaffold — project structure and config only, no business
logic implemented yet.
