# لغة ضاد — LughatDad

Online editor and interpreter for an Arabic programming language, deployed as a full-stack Java service on Render.

## Deploy on Render

This repository is ready for Render using `render.yaml` in the project root.

### 1) Push code to GitHub

```bash
git add .
git commit -m "prepare render deployment"
git push origin main
```

### 2) Create Render Blueprint

1. Open [Render Dashboard](https://dashboard.render.com/)
2. Click **New +** -> **Blueprint**
3. Select this GitHub repository
4. Confirm deploy

### 3) Render service behavior

- Environment: `docker`
- Dockerfile: `Dockerfile`
- Health check: `/api/health`

The app is served from one URL:

- `/` -> frontend
- `/api/run` -> backend compiler execution API
