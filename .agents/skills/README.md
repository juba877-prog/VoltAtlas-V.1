# VoltAtlas Workspace Skills

This directory contains workspace-specific skills for Antigravity.

## Structure
Each skill has its own subfolder containing a `SKILL.md` file:
```
.agents/
└── skills/
    ├── voltatlas-gate/
    │   └── SKILL.md
    └── <your-new-skill>/
        └── SKILL.md
```

## Creating a new skill
To create a new skill, create a directory under `.agents/skills/<skill-name>/` and add a `SKILL.md` file starting with YAML frontmatter:

```markdown
---
name: your-skill-name
description: Clear explanation of what this skill does and when the agent should use it.
---

# Instructions
Step-by-step guidance, rules, and commands.
```
