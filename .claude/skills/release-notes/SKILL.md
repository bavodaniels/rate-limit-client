---
name: release-notes
description: Generate release notes from git commits since last tag for the rate-limit-client library
disable-model-invocation: false
---

Run: git log $(git describe --tags --abbrev=0)..HEAD --oneline --no-merges

Then draft release notes grouped by: Features, Bug Fixes, Chores/CI.
Focus on changes relevant to library consumers (API changes, new Spring Boot auto-configuration, new interceptor/filter support).
Format as GitHub release markdown.
