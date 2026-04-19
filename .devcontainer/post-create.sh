#!/usr/bin/env bash

set -euo pipefail

java --version
clojure -Sdescribe >/dev/null
bb --version

if [[ -f deps.edn ]]; then
  clojure -P
fi