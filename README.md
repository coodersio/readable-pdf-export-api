# PDF API

Spring Boot service for PDF processing.

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

See `DOKPLOY.md` for the full deployment checklist.

## License

AGPL-3.0. See `LICENSE`.
