# My Contribution Summary — LughatDad (لغة ضاد)

A detailed account of everything I built in this project: from a raw JavaCC grammar file to a fully deployed, browser-based Arabic programming language IDE.

---

## 1. Project Overview

**LughatDad** (لغة ضاد) is a complete Arabic programming language interpreter with a web-based IDE. The language supports:

- Variable declaration (`متغير`)
- Print statements (`اطبع`)
- Conditional logic (`إذا / وإلا`)
- While loops (`بينما`)
- Arithmetic & comparison operators
- Arabic and Unicode digit support (٠١٢٣...)
- Boolean literals (`صح / خطأ`)

The entire system is built from a **single source of truth**: the `LughatDad.jj` grammar file. Everything else — the parser, interpreter, HTTP server, frontend, and cloud deployment — was built on top of it.

---

## 2. Full Architecture Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                        LughatDad.jj                                 │
│   (JavaCC grammar — defines the entire language spec)               │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  JavaCC compiles it
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           backend/LughatDad.java  (auto-generated)                  │
│   + LughatDadTokenManager.java   (lexer)                            │
│   + LughatDadConstants.java      (token IDs)                        │
│   + ParseException.java          (error model)                      │
│   + SimpleCharStream.java        (character reader)                 │
│   + Token.java / TokenMgrError.java                                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  used by
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           backend/LughatDadRunner.java   (subprocess entry point)   │
│   - Receives: path to a temp .txt file containing user's code       │
│   - Resets: symbol table (LughatDad.symbolTable = new HashMap)      │
│   - Parses and executes: parser.program() → program.execute()       │
│   - Exits: 0 on success, 1 on parse/runtime error                   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  spawned per request by
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           backend/LughatDadServer.java   (HTTP server)              │
│                                                                     │
│   Endpoints:                                                        │
│   ┌──────────────┬────────────────────────────────────────────────┐ │
│   │ GET  /       │ Serves webapp/index.html (the IDE)             │ │
│   │ GET  /api/   │ Serves any static file from webapp/            │ │
│   │ POST /api/run│ Runs user code → returns JSON output           │ │
│   │ GET /api/    │ Returns {"status":"ok",...} for health checks  │ │
│   │     health   │                                                │ │
│   └──────────────┴────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  serves
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           webapp/index.html   (Frontend IDE)                        │
│   - CodeMirror editor (Arabic RTL support)                          │
│   - "Run" button → fetch POST /api/run → display output             │
│   - Error display in Arabic                                         │
└─────────────────────────────────────────────────────────────────────┘
                           │  containerized by
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           Dockerfile  →  Docker image                               │
│   - Base: eclipse-temurin:17-jdk                                    │
│   - Builds: JavaCC → compiles .jj → javac all .java                 │
│   - Runs: java LughatDadServer on port $PORT                        │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  deployed via
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           render.yaml  →  Render.com Cloud Deployment               │
│   - type: web, env: docker                                          │
│   - healthCheckPath: /api/health                                    │
│   - Auto-triggered on every push to GitHub main branch              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. The `.jj` File — How the Language Is Defined

`LughatDad.jj` is a **JavaCC** (Java Compiler Compiler) grammar file. It is the heart of the entire project. Here is what it contains and what each part does:

### 3.1 File Header — Options & Parser Class

```
options {
  UNICODE_INPUT = true;   // enables full Arabic Unicode support
  STATIC = true;          // parser methods are static (one shared class)
}

PARSER_BEGIN(LughatDad)
...
PARSER_END(LughatDad)
```

The `PARSER_BEGIN` / `PARSER_END` block contains a full Java class definition. Inside this class, I defined all the **AST (Abstract Syntax Tree) node classes** directly — the parser and interpreter are fused into one.

### 3.2 Part 1 — Lexical Analyzer (Lexer / Tokenizer)

This section defines what the language **looks like** at the character level:

| Rule | What it does |
|------|-------------|
| `SKIP` | Ignores spaces, tabs, newlines, and `//` comments |
| `TOKEN: KEYWORDS` | Maps Arabic Unicode strings to token types (e.g., `متغير` → `MUTA_GHAYYER`) |
| `TOKEN: IDENTIFIERS` | Matches Arabic variable names using Unicode range `\u0620–\u065F` |
| `TOKEN: LITERALS` | Matches numbers (int/float), strings (`"..."`) |
| `TOKEN: OPERATORS` | Matches `+`, `-`, `*`, `/`, `=`, `==`, `!=`, `<`, `>`, `<=`, `>=` |
| `TOKEN: PUNCTUATION` | Matches `(`, `)`, `{`, `}`, `;` and the Arabic semicolon `؛` |

**Key design decision**: Arabic keywords are stored as their raw Unicode codepoints in the `.jj` file. For example:
```
< MUTA_GHAYYER: "\u0645\u062a\u063a\u064a\u0631" >  /* متغير */
< ITBA3:        "\u0627\u0637\u0628\u0639" >          /* اطبع */
```
This ensures the lexer works correctly regardless of file encoding at the system level.

Arabic digits (`٠١٢٣٤٥٦٧٨٩`) are handled by a utility method `convertArabicDigits()` that maps them to Western digits before parsing as a `Double`.

### 3.3 Part 2 — Parser Grammar (BNF Rules)

The parser section defines the **grammar rules** (what sequences of tokens are valid programs):

```
program()    → ( statement() )* EOF
statement()  → declaration | assignment | printStmt | ifStmt | whileStmt
declaration  → متغير IDENTIFIER = expression ;
assignment   → IDENTIFIER = expression ;
printStmt    → اطبع ( expression ) ;
ifStmt       → إذا ( condition ) block [ وإلا block ]
whileStmt    → بينما ( condition ) block
block        → { ( statement )* }
expression   → term ( (+|-) term )*
term         → unary ( (*|/) unary )*
unary        → [-] primary
primary      → NUMBER | STRING | IDENTIFIER | ( expression ) | صح | خطأ
condition    → expression [ relop expression ]
relop        → > | < | == | != | >= | <=
```

Each grammar rule returns a Java `Stmt` or `Expr` object — this is how the parser and interpreter are unified in one step.

### 3.4 Part 3 — Interpreter / Semantic Actions

The Java classes embedded inside `PARSER_BEGIN` / `PARSER_END` form a **tree-walking interpreter**:

| Class | Type | Role |
|-------|------|------|
| `Expr` | Abstract | Base for all expressions |
| `NumExpr` | Expr | Holds a `double` number |
| `StrExpr` | Expr | Holds a `String` |
| `BoolExpr` | Expr | Holds a `boolean` |
| `VarExpr` | Expr | Looks up variable in `symbolTable` |
| `BinOp` | Expr | Evaluates binary operations (`+`, `-`, `>`, `==`, ...) |
| `Stmt` | Abstract | Base for all statements |
| `DeclStmt` | Stmt | Declares a variable → puts in `symbolTable` |
| `AssignStmt` | Stmt | Assigns a new value to an existing variable |
| `PrintStmt` | Stmt | Evaluates an expression → prints to stdout |
| `IfStmt` | Stmt | Evaluates condition → executes `thenBlock` or `elseBlock` |
| `WhileStmt` | Stmt | Loops while condition is true |
| `BlockStmt` | Stmt | Holds a list of statements → executes them in order |

The **symbol table** is a `static HashMap<String, Object>` shared across all statements in one run.

### 3.5 How JavaCC Processes the `.jj` File

```
LughatDad.jj
    ↓  [JavaCC tool - javacc.jar]
LughatDad.java          ← the parser class (your grammar rules as Java methods)
LughatDadTokenManager.java  ← the lexer (token matching engine)
LughatDadConstants.java     ← integer constants for each token type
ParseException.java         ← exception thrown on syntax errors
SimpleCharStream.java       ← character-by-character stream reader
Token.java                  ← represents one matched token
TokenMgrError.java          ← error if a character doesn't match any token
```

All these files are **auto-generated** — I do not edit them manually. They are produced by running:
```bash
java -cp javacc.jar org.javacc.parser.Main LughatDad.jj
```

---

## 4. Backend — How Code Gets Executed

The backend uses `LughatDadServer.java` (a lightweight HTTP server using Java's built-in `com.sun.net.httpserver`) combined with `LughatDadRunner.java` (a fresh subprocess per request).

### 4.1 Why a Subprocess?

The `.jj` file uses `STATIC = true`, which means all parser state (including the symbol table and input stream) is stored in **static fields** of the `LughatDad` class. If we reused the same JVM process, leftover state from one user's run could corrupt the next run.

**Solution**: For every code execution request, the server spawns a **fresh JVM subprocess** (`LughatDadRunner`) that starts with completely clean static state. After the run finishes, the process exits and its memory is freed.

### 4.2 Execution Flow for `POST /api/run`

```
Browser sends:  POST /api/run
                Body: { "code": "متغير س = 5؛\nاطبع(س)؛" }
                           │
                           ▼
              LughatDadServer.RunHandler.handle()
                           │
                  Extracts "code" from JSON body
                           │
                           ▼
              runSubprocess(code):
              1. Creates temp file:  /tmp/lughatdad_XXXX.txt
              2. Writes user's Arabic code into it
              3. Spawns subprocess:
                 java -Dfile.encoding=UTF-8 \
                      -cp /app/backend \
                      LughatDadRunner \
                      /tmp/lughatdad_XXXX.txt
              4. Reads stdout (merged with stderr)
              5. Waits max 10 seconds (kills if timeout)
              6. Checks exit code + output prefix
              7. Cleans output (removes debug lines)
              8. Deletes the temp file
                           │
                           ▼
              Returns JSON:
              {
                "success": true,
                "output": ["5"],
                "error": null
              }
                           │
                           ▼
              Browser receives JSON → displays output in IDE
```

### 4.3 What `LughatDadRunner` Does

```
LughatDadRunner.main(args):
1. Reads file path from args[0]
2. Resets:  LughatDad.symbolTable = new HashMap<>()
3. Opens the temp file as an InputStream
4. Creates parser:  new LughatDad(new InputStreamReader(stream, UTF_8))
5. Parses:  BlockStmt program = parser.program()
6. Executes:  program.execute()
7. Exits 0 (success) or 1 (on any error)

Errors are caught and printed in Arabic:
  ParseException  →  خطأ نحوي: ...
  RuntimeException →  خطأ: ...
  FileNotFound    →  خطأ: ملف المصدر غير موجود
```

### 4.4 `GET /api/health`

Returns a simple JSON status used by Render to verify the service is alive before marking it as deployed:
```json
{ "status": "ok", "compiler": "LughatDad.jj", "javacc": "7.0.13", "version": "1.0" }
```

### 4.5 `GET /` — Serving the Frontend

The `StaticHandler` maps any `GET` request to a file inside `../webapp/`. A request to `/` or `/index.html` returns the main IDE page. MIME types are guessed from file extension (`.html`, `.css`, `.js`, `.json`, etc.).

---

## 5. Frontend — The Browser IDE

Located in `webapp/index.html` (a single self-contained HTML file).

**What it includes:**
- A **CodeMirror** editor configured for RTL Arabic input
- A "Run" button (تشغيل) that triggers execution
- An output panel that displays results or error messages
- Arabic-language UI labels and error messages

**How it communicates with the backend:**
```javascript
// When user clicks "Run":
const response = await fetch('/api/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: editor.getValue() })
});

const result = await response.json();

if (result.success) {
    displayOutput(result.output.join('\n'));
} else {
    displayError(result.error);
}
```

The frontend makes a **relative URL** request to `/api/run` — this works in both local development (port 5050) and on Render (port from `$PORT` env var), because the Java server serves both the frontend files and the API from the same port.

---

## 6. The Dockerfile — How the Container Is Built

```dockerfile
FROM eclipse-temurin:17-jdk         # JDK 17 base (Eclipse Temurin = trusted OpenJDK)

WORKDIR /app                         # all files go into /app

COPY . /app                          # copy entire repo into image

RUN cd backend \
    && java -cp javacc.jar org.javacc.parser.Main LughatDad.jj \
    && javac -encoding UTF-8 *.java  # compile everything at image build time

EXPOSE 10000                         # document the port (Render assigns $PORT dynamically)

CMD ["sh", "-c", "cd backend && java -cp . LughatDadServer"]
```

**Build-time steps (inside `RUN`):**
1. `cd backend` — move into the backend directory (where `javacc.jar` and `LughatDad.jj` are)
2. `java -cp javacc.jar ... Main LughatDad.jj` — run JavaCC to generate all the parser Java files
3. `javac -encoding UTF-8 *.java` — compile all generated + hand-written Java files into `.class` files

**Run-time step (inside `CMD`):**
- Starts `LughatDadServer` from the `backend/` directory
- The server reads `$PORT` env var (set by Render) or defaults to `5050`

**Why this design works:**
- The `.jj` compilation and `.java` compilation happen **inside the Docker image build** — no pre-built files need to be committed to Git
- The final image is fully self-contained: all `.class` files are baked in
- The `webapp/` folder is at `../webapp` relative to `backend/`, and the server resolves it using `Paths.get("").toAbsolutePath().resolve("../webapp")`

---

## 7. `render.yaml` — Cloud Deployment Configuration

```yaml
services:
  - type: web           # web service (has a public URL)
    name: lughatdad
    env: docker         # tells Render to use the Dockerfile
    plan: free          # use the free tier
    rootDir: .          # Dockerfile is at the repo root
    dockerfilePath: ./Dockerfile
    healthCheckPath: /api/health  # Render pings this before marking service live
```

This single file tells Render **everything it needs**:
- What kind of service to create
- Where the Dockerfile is
- How to verify the service started correctly

Render automatically injects a `PORT` environment variable — the server reads this with:
```java
String envPort = System.getenv("PORT");
```

---

## 8. End-to-End Deployment Flow

```
Step 1: Push code to GitHub (main branch)
           │
           ▼
Step 2: Render detects new commit (auto-deploy or manual trigger)
           │
           ▼
Step 3: Render reads render.yaml → finds: env: docker
           │
           ▼
Step 4: Render builds Docker image:
        docker build -t lughatdad .
        [Inside: JavaCC runs, .jj → .java files generated, all compiled]
           │
           ▼
Step 5: Render starts container:
        sh -c "cd backend && java -cp . LughatDadServer"
        [Server starts on $PORT, typically 10000]
           │
           ▼
Step 6: Render sends GET /api/health
        Expected response: { "status": "ok" }
        If response received → service is marked LIVE
           │
           ▼
Step 7: Render assigns public URL (e.g., https://lughatdad.onrender.com)
           │
           ▼
Step 8: User visits URL → GET / → LughatDadServer serves webapp/index.html
Step 9: User writes code → clicks Run → POST /api/run → output displayed
```

---

## 9. What I Did Step by Step (Chronological)

| # | What I did | Files involved |
|---|-----------|---------------|
| 1 | Started with the grammar specification. Defined all language tokens: keywords (Arabic Unicode), identifiers, numbers, strings, operators | `LughatDad.jj` — LEXER section |
| 2 | Wrote the BNF grammar rules for the parser: program, statements, expressions, conditions, blocks | `LughatDad.jj` — PARSER section |
| 3 | Embedded the interpreter classes directly in the `.jj` file as Java code: `Expr`, `Stmt`, `BinOp`, `PrintStmt`, `IfStmt`, `WhileStmt`, etc. | `LughatDad.jj` — PARSER_BEGIN block |
| 4 | Ran JavaCC to generate the parser code from the `.jj` file | `backend/*.java` (auto-generated) |
| 5 | Compiled all Java files and tested the interpreter locally by running `LughatDad` directly against a test file | `backend/*.class` |
| 6 | Wrote `LughatDadRunner.java` as a thin subprocess entry point to isolate static state between runs | `backend/LughatDadRunner.java` |
| 7 | Wrote `LughatDadServer.java` as a full HTTP server: POST `/api/run`, GET `/api/health`, GET `/` static file serving | `backend/LughatDadServer.java` |
| 8 | Fixed the critical STATIC=true isolation bug by using subprocess spawning in `runSubprocess()` | `LughatDadServer.java` |
| 9 | Built the frontend IDE in a single HTML file with CodeMirror + Arabic RTL support + fetch API integration | `webapp/index.html` |
| 10 | Wrote the `Dockerfile`: JDK base image, copy repo, run JavaCC inside build, compile Java, expose port, start server | `Dockerfile` |
| 11 | Wrote `render.yaml` to configure Render.com: docker env, health check path, free plan | `render.yaml` |
| 12 | Configured server to read `$PORT` from environment for cloud compatibility | `LughatDadServer.java` → `resolvePort()` |
| 13 | Added CORS headers to all API responses for browser cross-origin requests | `LughatDadServer.java` → `addCors()` |
| 14 | Pushed to GitHub and deployed. Verified health check, editor load, and code execution on Render | GitHub + Render dashboard |
| 15 | Wrote documentation: README, this summary | `README.md`, `PROJECT_CONTRIBUTION_SUMMARY.md` |

---

## 10. Final Result

I transformed a single compiler grammar file (`LughatDad.jj`) into a **complete full-stack Arabic programming language platform**:

- A working **lexer + parser + interpreter** compiled from a `.jj` grammar
- A **Java HTTP server** that safely executes user code in isolated subprocesses
- A **browser IDE** with Arabic RTL support and real-time output
- A **Dockerized deployment** that compiles the language at image build time
- A **cloud deployment** on Render.com, auto-triggered from GitHub pushes

> The entire system — from token matching to cloud execution — flows from one source file: `LughatDad.jj`.
