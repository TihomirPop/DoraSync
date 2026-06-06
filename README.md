# DoraSync

DoraSync is a tool for collecting data needed for Dora metrics from CI/CD pipelines.

## Local Development

Start a local PostgreSQL container:

```bash
docker run --name dora-postgres \
  -e POSTGRES_DB=dora \
  -e POSTGRES_USER=dora \
  -e POSTGRES_PASSWORD=dora \
  -p 5432:5432 \
  -d postgres:18-alpine
```

Stop and remove it when it is no longer needed:

```bash
docker stop dora-postgres
docker rm dora-postgres
```
