#!/usr/bin/env bash
# Проверяет, что относительные ссылки в Markdown указывают на существующие файлы.
#
# Известные ограничения:
#   - ссылки внутри блоков кода с отступом (4 пробела, без ``` / ~~~) не пропускаются;
#   - ссылки, у которых цель и закрывающая ")" разбиты переносом строки, не распознаются;
#   - URL-encoded пути (например, %20) не декодируются перед проверкой существования файла.
set -euo pipefail
cd "$(dirname "$0")/.."

broken=0
while IFS= read -r -d '' md; do
  dir=$(dirname "$md")
  # вытаскиваем цели ссылок вида ](target) и ](target "title"), пропуская строки
  # внутри ``` / ~~~ блоков кода (в них ссылочный синтаксис часто приводится как пример)
  while IFS= read -r target; do
    path="${target%%#*}"      # отбросить якорь #anchor
    path="${path%%[[:space:]]*}"  # у титульной ссылки ](path "title") оставить только path
    [[ -z "$path" ]] && continue
    [[ "$path" =~ ^(https?:|mailto:) ]] && continue
    if [[ ! -e "$dir/$path" ]]; then
      echo "BROKEN: $md -> $target"
      broken=1
    fi
  done < <(awk '
    /^[[:space:]]*(```|~~~)/ { infence = !infence; next }
    !infence { print }
  ' "$md" | grep -oE '\]\([^)]+\)' | sed -E 's/^\]\((.*)\)$/\1/')
done < <(find . -name '*.md' -not -path '*/target/*' -not -path './docs/superpowers/*' -not -path './.superpowers/*' -print0)

if [[ $broken -eq 0 ]]; then echo "All links OK"; fi
exit $broken
