#### 1. Goal
Show attestation lists beside each inflected form on the word page, and stop showing a global list of all attested forms for the lemma.

#### 2. Approach
Add `attestations` to `LinkWordViewModel` because the existing word page renders inflected forms through link rows, not through the top-level `WordViewModel.attestations` field. This is more robust than frontend-only grouping by attested text because `InMemoryGraphRepository.findAttestations(word)` currently returns at most one attestation per corpus text for the aggregate lemma lookup (`model/src/main/kotlin/InMemoryGraphRepository.kt:165-176`), which can lose per-form detail when a corpus text contains multiple forms. The frontend will reuse the existing `AttestationViewModel` and `WordTextView` rendering pattern already used by the global paragraph (`frontend/pages/[graph]/word/[lang]/[...text].tsx:568-575`).

#### 3. File Changes
- **Modify** `web/src/main/kotlin/controllers/WordController.kt:61-79`: extend `LinkWordViewModel` with `attestations: List<AttestationViewModel>` and extract the current attestation mapping from `Word.toViewModel()` into a helper to avoid duplicating mapping logic.
- **Modify** `web/src/main/kotlin/controllers/WordController.kt:209-216`: replace inline top-level `attestations.map { ... }` with the new helper for consistency.
- **Modify** `web/src/main/kotlin/controllers/WordController.kt:599-619`: populate link-level attestations in `linkToViewModel()`, using `graph.findAttestations(toWord)` for derived inflected-form links and `emptyList()` for unrelated link rows where per-form attestations should not be displayed.
- **Modify** `web/etymograph-openapi.yaml:1985-2026`: add `attestations: array<AttestationViewModel>` to `LinkWordViewModel` and include it in `required`, matching the Kotlin response shape.
- **Modify (generated, not by direct editing)** `frontend/models/types.gen.ts:267-277`: regenerate types so `LinkWordViewModel` includes `attestations: Array<AttestationViewModel>`. Per `AGENTS.md`, this must be done by running `npx @hey-api/openapi-ts -i ../web/etymograph-openapi.yaml` from `frontend`, not by editing `frontend/models` directly.
- **Modify (generated, not by direct editing)** `frontend/models/index.ts:1-2`: no semantic change expected, but include it if the generator touches it.
- **Modify** `frontend/pages/[graph]/word/[lang]/[...text].tsx:47-52`: import `AttestationViewModel` if needed for a typed helper component.
- **Modify** `frontend/pages/[graph]/word/[lang]/[...text].tsx:80-180`: add a small reusable `AttestationList` or `InlineAttestations` component near `WordLinkComponent`, rendering corpus text links and optional surface-form text with `WordTextView` exactly like the current global block.
- **Modify** `frontend/pages/[graph]/word/[lang]/[...text].tsx:130-166`: render link-level attestations in `WordLinkComponent` only when `params.linkType.typeId === '>'`, `!params.directionFrom`, and `linkWord.attestations.length > 0`, so the display appears beside inflected forms and not beside the lemma/origin/related rows.
- **Modify** `frontend/pages/[graph]/word/[lang]/[...text].tsx:568-575`: remove the global `Attested in ...` paragraph that currently displays aggregate `word.attestations` for the whole lemma.
- **Modify** `web/src/test/kotlin/WordControllerTest.kt:15-120`: add controller coverage proving a lemma’s `linksTo` derived inflected form carries its own attestations.

#### 4. Implementation Steps
**Task 1: Backend model and mapping**
1. In `web/src/main/kotlin/controllers/WordController.kt:61-72`, add `val attestations: List<AttestationViewModel>` to `LinkWordViewModel` after `notes` or before `suggestedSequences`.
2. In `web/src/main/kotlin/controllers/WordController.kt:78`, keep `AttestationViewModel` unchanged so the existing OpenAPI and frontend rendering shape remains reusable.
3. Add a private/top-level helper in `web/src/main/kotlin/controllers/WordController.kt`, for example `fun Word.attestationsToViewModel(graph: GraphRepository): List<WordController.AttestationViewModel>`, containing the mapping now at `web/src/main/kotlin/controllers/WordController.kt:209-216`.
4. Replace the top-level `WordViewModel` constructor argument at `web/src/main/kotlin/controllers/WordController.kt:209-216` with `attestationsToViewModel(graph)`.
5. In `web/src/main/kotlin/controllers/WordController.kt:599-619`, compute `val attestations = if (link.type == Link.Derived && !fromSide) toWord.attestationsToViewModel(graph) else emptyList()` and pass it into `LinkWordViewModel`.
6. Keep non-inflected link rows returning an empty `attestations` list so clients can treat the field as required and never null.

**Task 2: API contract and generated frontend types**
1. In `web/etymograph-openapi.yaml:1985-2026`, add `attestations` to the `LinkWordViewModel.properties` object with `type: array` and `items: $ref: "#/components/schemas/AttestationViewModel"`.
2. In `web/etymograph-openapi.yaml:2020-2026`, add `attestations` to the `required` list for `LinkWordViewModel`.
3. From `frontend`, run `npx @hey-api/openapi-ts -i ../web/etymograph-openapi.yaml` to regenerate `frontend/models/types.gen.ts` and any generated index output, following `AGENTS.md`.
4. Confirm `frontend/models/types.gen.ts:267-277` now includes `attestations: Array<AttestationViewModel>` on `LinkWordViewModel`.

**Task 3: Frontend rendering**
1. In `frontend/pages/[graph]/word/[lang]/[...text].tsx:47-52`, include `AttestationViewModel` in the existing `@/models` import if the new helper is typed.
2. Add `InlineAttestations({attestations}: { attestations: AttestationViewModel[] })` near `WordLinkComponent` in `frontend/pages/[graph]/word/[lang]/[...text].tsx:80-180`.
3. Implement `InlineAttestations` with the same output semantics as `frontend/pages/[graph]/word/[lang]/[...text].tsx:568-575`: `Attested in`, comma-separated `Link` entries to `/${graph}/corpus/text/${att.textId}`, and optional `WordTextView` for `att.word`/`att.syllabogramSequence`.
4. In `WordLinkComponent` at `frontend/pages/[graph]/word/[lang]/[...text].tsx:130-166`, render `<InlineAttestations attestations={linkWord.attestations}/>` after the linked word/rule/source metadata when `params.linkType.typeId === '>' && !params.directionFrom && linkWord.attestations.length > 0`.
5. Remove the aggregate block at `frontend/pages/[graph]/word/[lang]/[...text].tsx:568-575` so lemma pages no longer show a global attestation list for all attested forms.
6. Preserve the existing `WordLinkTypeComponent` grouping at `frontend/pages/[graph]/word/[lang]/[...text].tsx:286-298`; the new per-link rendering happens inside each row and does not require changing the link grouping rules.

**Task 4: Tests**
1. In `web/src/test/kotlin/WordControllerTest.kt:55-80` or near the existing link tests at `web/src/test/kotlin/WordControllerTest.kt:82-110`, add a test that creates a lemma word, a derived inflected word, a `Link.Derived` link from the inflected word to the lemma, and a corpus text associated with the inflected word.
2. Call `wordController.singleWordJson(graph, lemma.language.shortName, lemma.text, lemma.id)` and assert `linksTo.single { it.typeId == ">" }.words.single().attestations.single().textId` matches the corpus text id.
3. Assert the linked attestation’s `word` is null when the corpus surface text matches the inflected word, matching current `AttestationViewModel` behavior from `web/src/main/kotlin/controllers/WordController.kt:213-214`.
4. Optionally add a second derived form in the same corpus text to verify each `LinkWordViewModel` receives its own direct attestation list rather than relying on the aggregate lemma-level `word.attestations` behavior in `model/src/main/kotlin/InMemoryGraphRepository.kt:165-176`.

#### 5. Acceptance Criteria
- On a lemma word page with derived links in `word.linksTo` of type `>` (`frontend/pages/[graph]/word/[lang]/[...text].tsx:578-579`), each inflected form row with attestations displays an inline `Attested in ...` list beside that form.
- The global top-level attestation paragraph currently at `frontend/pages/[graph]/word/[lang]/[...text].tsx:568-575` is absent after implementation.
- A derived form with one associated corpus text produces exactly one `LinkWordViewModel.attestations` entry in the lemma page API response.
- A derived form with no corpus attestations produces `attestations: []`, not `null` or an omitted field.
- Non-derived link rows such as origin (`^`), related (`~`), variation (`=`), and transcription (`_`) do not display per-form attestation text unless deliberately changed later.
- `frontend/models/types.gen.ts` is regenerated from `web/etymograph-openapi.yaml` and not manually edited.
- Existing word link edit/delete/apply-sequence controls in `WordLinkComponent` continue to render because the new attestation list is additive and does not change `WordLinkProps` behavior.

#### 6. Verification Steps
- Run backend word controller tests: `./gradlew :web:test --tests WordControllerTest`.
- Run broader backend tests if time permits: `./gradlew :web:test`.
- Compile backend API code: `./gradlew :web:compileKotlin`.
- From `frontend`, regenerate models with `npx @hey-api/openapi-ts -i ../web/etymograph-openapi.yaml` after OpenAPI changes.
- Run frontend lint/type checks available in the project: `npm run lint` from `frontend`; if no dedicated typecheck exists, run `npm run build` from `frontend` to catch TypeScript/Next.js issues.
- Manual check: open a lemma word that has `Inflected forms` and corpus attestations; confirm each attested form row has its own corpus links and there is no global attestation paragraph above the link sections.
- Manual edge check: open an inflected form’s own page; confirm its `Lemma` link does not show the per-form attestation list because the rendering condition requires `!directionFrom`.

#### 7. Risks & Mitigations
- **Repeated corpus scans:** `graph.findAttestations(toWord)` scans corpus texts (`model/src/main/kotlin/InMemoryGraphRepository.kt:165-176`) and will be called for each derived link. Mitigation: keep it scoped to `Link.Derived && !fromSide`; if performance becomes visible, batch by corpus texts or memoize per word id inside `Word.toViewModel()`.
- **Contract drift:** frontend generated types must match `web/etymograph-openapi.yaml`. Mitigation: update the OpenAPI schema before running `npx @hey-api/openapi-ts -i ../web/etymograph-openapi.yaml`, and do not hand-edit `frontend/models`.
- **Visual clutter on words with many attestations:** inline lists may be long. Mitigation: initially preserve the existing compact comma-separated rendering; if it becomes too noisy, add a collapsible or truncated display as a separate follow-up.
- **Ambiguous direct lemma attestations:** removing the global paragraph means direct attestations of the lemma itself no longer appear unless represented as an inflected-form row. Mitigation: if this is undesirable during review, keep a direct-only headword attestation block filtered to entries where `att.word == null`, but do not retain the aggregate all-forms list.