#!/usr/bin/env bash
set -euo pipefail

docker compose build app
docker compose run --rm app --seed.spark-junior-1600=true
