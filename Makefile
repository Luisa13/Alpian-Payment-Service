.PHONY: up down build test test-parallel run smoke smoke-outbox smoke-outbox-dlq smoke-burst smoke-resilience format help

COMPOSE = docker compose
GRADLEW = ./gradlew

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start all infrastructure containers (PostgreSQL, Kafka, Grafana, Prometheus)
	$(COMPOSE) up -d --wait
	@echo ""
	@echo "  PostgreSQL  → localhost:5432"
	@echo "  Kafka       → localhost:9092"
	@echo "  Kafka UI    → http://localhost:8090"
	@echo "  Prometheus  → http://localhost:9090"
	@echo "  Grafana     → http://localhost:3000  (admin/admin)"

down: ## Stop and remove all containers and networks (data volumes are preserved)
	$(COMPOSE) down

build: ## Compile, format-check, run tests, check coverage (./gradlew build)
	$(GRADLEW) clean build

test: ## Run all tests (unitTest + integrationTest + e2eTest)
	$(GRADLEW) unitTest integrationTest e2eTest

test-parallel: ## Run all tests in parallel
	$(GRADLEW) unitTest integrationTest e2eTest --parallel

run: ## Build the app image (Jib) and start it alongside the infra stack
	$(GRADLEW) jibDockerBuild
	$(COMPOSE) --profile app up -d --wait

smoke: ## Run smoke tests against the locally running app (requires `make run`)
	@bash scripts/smoke.sh

smoke-outbox: ## Run outbox relay happy-path smoke test (requires running stack + app)
	@bash scripts/smoke-outbox.sh

smoke-outbox-dlq: ## Run outbox dead-letter smoke test (stops/starts Kafka)
	@bash scripts/smoke-outbox-dlq.sh

smoke-burst: ## Run concurrent burst smoke test (BURST_SIZE=10 parallel payments)
	@bash scripts/smoke-burst.sh

smoke-resilience: ## Run HA, concurrency, error-handling, and external-rail smoke tests
	@bash scripts/smoke-resilience.sh

format: ## Auto-format all Java sources with palantir-java-format
	$(GRADLEW) spotlessApply
