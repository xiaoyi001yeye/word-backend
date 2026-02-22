#!/bin/bash

docker run -it --rm \
  -v "$(pwd):/app" \
  -w /app \
  -v "$HOME/.local/share/opencode:/root/.local/share/opencode" \
  -v "/home/weiyi/Download/maimemo-export-2.0.0/exported/translation:/opt/translation" \
  ghcr.io/anomalyco/opencode \
  "$@"
