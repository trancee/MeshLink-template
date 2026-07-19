# About the documentation structure

MeshLink documentation follows the [Diátaxis](https://diataxis.fr) framework.
Every doc serves exactly one of four needs — read the `diataxis` skill before
writing or restructuring anything here.

| Directory | Type | Serves |
|---|---|---|
| `docs/tutorials/` | Tutorial | Learning MeshLink hands-on, step by step |
| [`docs/how-to/`](how-to/) | How-to guide | Accomplishing a specific real-world task |
| [`docs/reference/`](reference/) | Reference | Looking up API shape, config options, error codes |
| [`docs/explanation/`](explanation/) | Explanation | Understanding *why* MeshLink is designed the way it is |

[`docs/decisions/`](decisions/) is a separate, fifth area: dated design/decision
records (research and design memos). It captures *how a decision was
reached*, not finished user-facing documentation — once a design lands, the
durable explanation of it belongs in `docs/explanation/`, not only in the
decision memo.

[`docs/rfcs/`](rfcs/) holds vendored, unmodified copies of external IETF/IRTF
specification texts (e.g. RFC 8439, RFC 8966) that MeshLink implements
against. These are reference material, not MeshLink's own decision records —
they never change once added, and MeshLink's own design memos never live
here.

Per the `diataxis` skill's own guidance, these directories are not
pre-created as empty scaffolding. Add a directory only when you have a real
document of that type to put in it, and classify new content with the
skill's compass before writing.
