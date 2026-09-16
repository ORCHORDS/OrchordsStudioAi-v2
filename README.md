<p align="center">
  <img src="https://raw.githubusercontent.com/ORCHORDS/docs/main/assets/1080x360.jpg" width="1080" alt="ORCHORDS — BUILD DIFFERENT.">
</p>

# ORCHORDS AI

[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)

> ⭐ If you like ORCHORDS AI or find it useful, please consider starring this repository. It helps more people discover the project.

> **Interested in sponsoring ORCHORDS?** Sponsorships start at **US$1,000**. Depending on the sponsorship level, sponsors may receive public recognition, logo and website placement, sponsor updates and early previews, roadmap-feedback briefings, priority issue triage, and engineering or integration discussions. Sponsorship does not buy control of the roadmap or guarantee feature implementation. Contact **[crm@orchords.com](mailto:crm@orchords.com)**.

**Independent software studio founded in 2025.**

ORCHORDS AI is a private-by-design, local-first Android AI workspace for user-selected models and services. It combines multi-provider chat, Model Context Protocol (MCP) tools, local persistence, voice, search, rich rendering, image, video, and workspace workflows without tying the product to a single model vendor.

## Start here

| If you need… | Start with |
| --- | --- |
| Build and verification requirements | [Building ORCHORDS AI](docs/BUILDING.md) |
| Component boundaries | [ORCHORDS AI Architecture](docs/ARCHITECTURE.md) |
| MCP integration and approvals | [Model Context Protocol](docs/MCP.md) |
| GitHub MCP guidance | [GitHub MCP](docs/GITHUB_MCP.md) |
| Cloudflare MCP guidance | [Cloudflare MCP](docs/CLOUDFLARE_MCP.md) |
| Security design and reporting | [Security Design](docs/SECURITY.md) · [Security Policy](SECURITY.md) |
| Release process | [Releasing ORCHORDS AI](docs/RELEASING.md) |
| Repository branding rules | [Branding and Documentation Style](docs/BRANDING.md) |

## Capabilities

- Multi-provider chat with configurable endpoints and request controls
- MCP tools with explicit approval boundaries for sensitive actions
- On-device conversations, settings, and credentials
- Voice, search, rich rendering, image, and video workflows
- Optional local web interface and workspace tooling

## Repository workflow

This repository works directly on `main`. GitHub Actions are disabled at the repository level and normal development does not depend on hosted or self-hosted runners. Contributors verify changes on their own host before pushing.

For issue work, run the lightweight local preflight first:

```bash
bash scripts/preflight-local.sh
```

Then run the relevant build, unit tests, lint, or focused verification for the code you changed. See [CONTRIBUTING.md](CONTRIBUTING.md) and [Building ORCHORDS AI](docs/BUILDING.md).

## Documentation boundary

Product-specific engineering guidance lives in this repository. Company-wide public engineering, security, governance, and operational documentation is maintained in [`ORCHORDS/docs`](https://github.com/ORCHORDS/docs).

## Brand

**ORCHORDS — BUILD DIFFERENT.**

## License

Licensed under the [GNU AGPL v3](LICENSE). See [Third-Party Notices](THIRD_PARTY_NOTICES.md).