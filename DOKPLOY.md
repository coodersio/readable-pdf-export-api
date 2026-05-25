# Dokploy Deployment

This service is distributed under AGPL-3.0.

## Dokploy Application Settings

- Source: GitHub repository
- Repository: `coodersio/readable-pdf-api`
- Branch: `main`
- Build type: `Dockerfile`
- Dockerfile path: `Dockerfile`
- Build context: `.`
- Target port: `8080`
- Health check URL: `/api/pdf/health`

The Dockerfile also defines a container healthcheck for `http://localhost:8080/api/pdf/health`. Dokploy can use the same path for its service health check.

Dokploy supports Dockerfile applications and service-level environment variables.

## Runtime Environment Variables

```env
READABLE_PDF_SOURCE=https://github.com/coodersio/readable-pdf-api
READABLE_PDF_VERSION=0.1.0
READABLE_PDF_COMMIT=<git-commit-sha>
READABLE_PDF_TAG=main
READABLE_PDF_IMAGE_DIGEST=<image-digest-or-unknown>
READABLE_PDF_LICENSE=AGPL-3.0
READABLE_PDF_MAX_PAGES=50
READABLE_PDF_CORS_ALLOWED_ORIGINS=null
READABLE_PDF_CORS_ALLOWED_ORIGIN_PATTERNS=<allowed-origin-patterns>
JAVA_OPTS=-XX:MaxRAMPercentage=75.0
```

## Build Args

```env
READABLE_PDF_SOURCE=https://github.com/coodersio/readable-pdf-api
READABLE_PDF_VERSION=0.1.0
READABLE_PDF_COMMIT=<git-commit-sha>
READABLE_PDF_TAG=main
READABLE_PDF_IMAGE_DIGEST=<image-digest-or-unknown>
```

## Public Endpoints

After deployment, verify:

```bash
curl https://<api-host>/api/pdf/health
curl https://<api-host>/api/pdf/source
curl https://<api-host>/api/pdf/version
```
