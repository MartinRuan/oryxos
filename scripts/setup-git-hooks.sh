#!/usr/bin/env bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
echo "Git hooks path configured to .githooks"
