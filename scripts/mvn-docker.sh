#!/usr/bin/env bash
# Запускает Maven внутри контейнера, а не на хосте.
#
# Зачем: некоторые антивирусы (например, Kaspersky Endpoint Security) блокируют
# служебные loopback-соединения, которые нужны Java NIO Selector. Тогда на хосте
# падает всё, что построено на Netty (в том числе Lettuce):
#   java.io.IOException: Unable to establish loopback connection
#   io.netty.channel.ChannelException: failed to create a child event loop
# Внутри Linux-контейнера селекторы работают через epoll, и проблемы нет.
#
# Testcontainers поднимает контейнеры БД рядом (docker-outside-of-docker):
# сокет Docker пробрасывается внутрь, а TESTCONTAINERS_HOST_OVERRIDE говорит
# библиотеке, по какому адресу до них достучаться.
#
# Использование:
#   bash scripts/mvn-docker.sh verify
#   bash scripts/mvn-docker.sh -pl redis/solutions test
#   bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest=ProductCacheTest
set -euo pipefail

cd "$(dirname "$0")/.."
project_dir=$(pwd -W 2>/dev/null || pwd)   # в Git Bash нужен путь в формате Windows
m2_dir="${HOME}/.m2"

if ! docker info >/dev/null 2>&1; then
  echo "Docker не запущен. Запустите Docker Desktop и повторите." >&2
  exit 1
fi

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "//var/run/docker.sock:/var/run/docker.sock" \
  -v "${project_dir}:/work" \
  -v "${m2_dir}:/root/.m2" \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e MAVEN_OPTS="-Dfile.encoding=UTF-8" \
  --add-host host.docker.internal:host-gateway \
  -w /work \
  maven:3.9-eclipse-temurin-21 \
  mvn "$@"
