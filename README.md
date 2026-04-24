# LughatDad (لغة ضاد)

LughatDad is a full-stack project for writing and running an Arabic programming language in the browser.
It is designed for students and beginners who want to learn programming and compiler concepts using Arabic syntax.

## Project Idea

Most programming tools are English-first. LughatDad reduces that learning barrier by giving Arabic-speaking users:

- Arabic syntax and keywords
- A simple online code editor
- Immediate output and readable errors

The project combines language design, parsing, interpretation, and web deployment in one practical learning platform.

## Features

- Arabic language syntax for core programming constructs
- Interactive web editor with run/execute flow
- Backend interpreter execution through API
- Health endpoint for monitoring and deployment checks
- Render-ready Docker deployment

## How to Use

1. Open the app URL (local or deployed).
2. Write LughatDad code in the editor.
3. Run the code.
4. Read output/errors in the result panel.

### Example Program

```arabic
متغير عداد = 1؛
بينما (عداد <= 3) {
    اطبع(عداد)؛
    عداد = عداد + 1؛
}
```

Expected output:

```text
1
هذا هو العدد اثنين
3
```

## Quick Start (Local)

### Requirements

- Java 17+

### Run

```bash
chmod +x start.sh
./start.sh
```

Then open: `http://localhost:5050`

## Deploy to Render

This repository is already configured with:

- `render.yaml`
- `Dockerfile`

### Steps

1. Push your latest code to GitHub.
2. Open [Render Dashboard](https://dashboard.render.com/).
3. Create **New + -> Blueprint**.
4. Select this repository and deploy.

After deployment:

- `/` opens the web editor
- `/api/health` confirms backend status

## API Endpoints

- `GET /` -> Web editor
- `POST /api/run` -> Execute LughatDad code
- `GET /api/health` -> Health check

### Example `POST /api/run` body

```json
{
  "code": "اطبع(\"مرحبا\")؛"
}
```

## Project Structure

- `backend/` - JavaCC grammar, parser, Java runtime, server
- `webapp/` - frontend editor and UI
- `start.sh` - local compile and run script
- `render.yaml` - Render Blueprint configuration
- `Dockerfile` - production deployment container

## Tech Stack

- Java
- JavaCC
- HTML/CSS/JavaScript
- Docker + Render