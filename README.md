# Readable PDF API

Open-source Spring Boot + iText PDF engine for Readable PDF Export.

This service should contain only PDF-processing logic:

- multipart request parsing
- Figma textless/background PDF merge
- visible real-text redraw pipeline
- invisible fallback text layer
- export report generation
- source/version/license disclosure

Keep product-business logic outside this repository:

- billing and quotas
- license keys
- Stripe or Lemon Squeezy webhooks
- user/account storage
- private analytics

## Endpoints

- `GET /api/pdf/health`
- `GET /api/pdf/source`
- `GET /api/pdf/version`
- `POST /api/pdf/readable/preflight`
- `POST /api/pdf/readable/export`

## Local Checks

```bash
mvn test
```

## Docker

```bash
docker build -t readable-pdf-api .
docker run --rm -p 8080:8080 readable-pdf-api
curl http://localhost:8080/api/pdf/health
```

## Dokploy

Use `Dockerfile` build type with target port `8080`.

Production plugin builds should point to:

```env
VITE_API_BASE_URL=https://api.readablepdfexport.com/api
```

See `DOKPLOY.md` for the full deployment checklist.

## License

AGPL-3.0. See `LICENSE`.
