---
name: changelog-writing
description: Use when writing changelogs that categorize changes, highlight breaking changes, and maintain a scannable format.
---

# Changelog Writing

## When to Use This Skill
- Writing changelog entries for a new release
- Categorizing changes (Added, Changed, Deprecated, Removed, Fixed, Security)
- Highlighting breaking changes with migration instructions
- Maintaining a consistent, scannable format

## Workflow
1. Collect all changes since the last release from commits and PRs
2. Categorize each change: Added, Changed, Deprecated, Removed, Fixed, Security
3. Write clear descriptions: what changed and why, not which commit
4. Highlight breaking changes at the top with migration instructions
5. Link to relevant issues or PRs for context
6. Follow Keep a Changelog format
7. Release with a version tag that matches the changelog

## Rules
- Write for users, not developers — describe what changed, not how
- Group changes by category, not by author or date
- Breaking changes go at the top — never hide them in the middle
- Include migration instructions for every breaking change
- Keep descriptions concise — one sentence per change when possible
- Don't include internal refactoring that doesn't affect users
