# Dokploy Deployment

This API is intended to be deployed as a public AGPL-3.0 source repository because it uses iText 8 under AGPL.

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

Dokploy supports Dockerfile applications and service-level environment variables. Use service variables for runtime config and build args for source disclosure metadata.

## Runtime Environment Variables

```env
READABLE_PDF_SOURCE=https://github.com/coodersio/readable-pdf-api
READABLE_PDF_VERSION=0.1.0
READABLE_PDF_COMMIT=<git-commit-sha>
READABLE_PDF_TAG=main
READABLE_PDF_IMAGE_DIGEST=<image-digest-or-unknown>
READABLE_PDF_LICENSE=AGPL-3.0
READABLE_PDF_MAX_PAGES=50
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
curl https://api.readablepdfexport.com/api/pdf/health
curl https://api.readablepdfexport.com/api/pdf/source
curl https://api.readablepdfexport.com/api/pdf/version
```

The Figma plugin production build should use:

```env
VITE_API_BASE_URL=https://api.readablepdfexport.com/api
```

## Data Handling Requirement

This service receives uploaded page PDFs plus Figma text metadata for PDF processing. Do not add billing, user accounts, private analytics, or persistent document storage to this open-source engine. Keep business logic in a separate closed service if needed.
