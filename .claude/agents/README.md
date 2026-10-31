# Model-routed agents

Three project agents, one per tier. The main session coordinates; it hands work to these
by name (or Claude picks by each agent's description).

| Agent | Model | Use for |
|---|---|---|
| `architect-debugger` | `claude-opus-5-5` | Architecture, feature plans, hard debugging |
| `implementer` | `claude-sonnet-5-5` | Regular implementation and refactoring (default) |
| `quick-tasks` | `claude-haiku-5-5` | Simple edits, docs, tests from spec, boilerplate |

Change a model by editing the `model:` line in the agent's frontmatter. Aliases
(`opus`, `sonnet`, `haiku`) also work and track the latest version of each family.

## Using the same agents in all your Android apps

Claude Code reads agents from two places:

- `.claude/agents/` in a repo: this project only.
- `~/.claude/agents/` on your machine: every project on that machine.

To share them, copy these three files (and this README) into `~/.claude/agents/`, or into
`.claude/agents/` of each app. Keep one master copy here and copy from it, so a model
change is made in one place. For automatic sharing across machines, publish the folder as
a Claude Code plugin later.

The agents refer to `CLAUDE.md`, so each app needs its own `CLAUDE.md` with its module rules.
