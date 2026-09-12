# IARC Content Rating — Pre-filled answers

**App:** OrchordsAI (`com.orchords.orchordsai`)
**Source of truth:** `docs/PRIVACY.md`, `docs/PERMISSIONS.md`,
`app/src/main/AndroidManifest.xml`.
**Last code-source review:** `git rev 0054798` (2026-09-13)

The Play Console asks the IARC questionnaire to determine the
content-rating label that appears on the listing. The expected rating
for OrchordsAI is **ESRB: Everyone / PEGI: 3 / IARC: 3+**, on the
basis that the app itself contains no media, no user-generated-content
moderation surface, and no advertising.

The notes below map to each IARC question. Use them when filling the
questionnaire.

---

## Category: Violence

**Q: Does the app contain violence?**
A: **No.** OrchordsAI does not depict, describe, or simulate violence.
Any violence-related text can only appear in messages the user sends
to the AI provider; that is user-generated content, not app content.

## Category: Sexual content

**Q: Does the app contain sexual content?**
A: **No.** OrchordsAI does not depict, describe, or simulate sexual
content. As with violence, anything of that kind originates from the
user's prompts and the AI provider's response, which are outside the
app's control.

## Category: Language

**Q: Does the app contain profanity or crude humor?**
A: **No.** The bundled UI text, error messages, and onboarding copy
are written for a general audience. AI-provider responses may contain
profanity depending on the user's prompts; that is user-generated
content.

## Category: Controlled substances

**Q: Does the app depict or promote the use of drugs, alcohol, or
tobacco?**
A: **No.** Same reasoning as violence and sexual content.

## Category: Gambling

**Q: Does the app simulate or promote gambling?**
A: **No.**

## Category: User-generated content

**Q: Does the app allow user-generated content to be shared with other
users?**
A: **No.** Conversations stay on the device. There is no social feed,
no shared workspace, no community marketplace. AI-provider responses
are private to the user.

## Category: Location

**Q: Does the app share the user's location with other users?**
A: **No.** OrchordsAI does not request location permission at all
(see `docs/PERMISSIONS.md`).

## Category: Personal data

**Q: Does the app allow users to share personal data with other users
or third parties?**
A: **Only the data the user explicitly chooses to send.** Messages are
sent to the AI provider the user configured; MCP requests are sent to
the MCP server the user configured. Neither is shared with other users
of OrchordsAI, and Orchords.com itself never receives the content.

## Category: Advertising

**Q: Does the app contain advertising?**
A: **No.** No ad SDK is bundled; there is no ad slot in any screen.

## Category: In-app purchases

**Q: Does the app offer in-app purchases?**
A: **No.** The Play Console listing should declare "No in-app
purchases". The app is free; provider API costs are paid by the user
directly to the AI provider.

## Category: Target audience

**Q: What is the target age group?**
A: **Everyone, including children 13+.** See "Children" section of
`docs/PRIVACY.md`: the app is not directed to children under 13.

---

## Resulting rating

After answering the above, IARC will produce:

- **ESRB:** Everyone
- **PEGI:** 3
- **IARC generic:** 3+
- **USK:** 0
- **ClassInd:** L (Livre)

No regional override or supplementary questions should be triggered.
If they are, revisit this sheet; a new permission or feature must have
been added without a privacy-policy update.
