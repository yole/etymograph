// @vitest-environment jsdom
import {renderToStaticMarkup} from "react-dom/server";
import {describe, expect, it, vi} from "vitest";
import Rule from "@/pages/[graph]/rule/[id]";
import {RuleExampleViewModel, RuleViewModel, WordRefViewModel} from "@/models";

vi.mock("next/router", () => ({useRouter: () => ({query: {graph: "test"}})}));
vi.mock("@/components/Breadcrumbs", () => ({default: () => null}));

function word(id: number, text: string): WordRefViewModel {
    return {id, text, language: "q", displayLanguage: "Quenya", gloss: "", homonym: false, reconstructed: false};
}

function renderExamples(examples: RuleExampleViewModel[], orphan = false) {
    const rule: RuleViewModel = {
        id: 1, name: "q-test", fromLang: "q", toLang: "q",
        fromLangFullName: "Quenya", toLangFullName: "Quenya", summaryText: "", editableText: "",
        fromPOS: [], source: [], sourceEditableText: "", paradigmId: null,
        spe: false, coreRule: false, shownOnMorphoPage: true,
        preInstructions: [], postInstructions: [], links: [], references: [], sequenceLinks: [],
        linkedWords: [], referencingParadigms: [],
        branches: orphan ? [] : [{conditions: {fragments: []}, instructions: [], examples}],
        orphanExamples: orphan ? examples : [],
    };
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<Rule loaderData={rule}/>);
    return container.querySelector("table")!;
}

describe("rule examples", () => {
    it.each([false, true])("sorts base words and groups their attested derivations (orphan: %s)", orphan => {
        const baseWord = {...word(10, "at"), gloss: "father"};
        const examples = [
            {fromWord: word(31, "za"), toWord: word(30, "z")},
            {fromWord: word(12, "atu"), toWord: baseWord},
            {fromWord: word(21, "bea"), toWord: word(20, "be")},
            {fromWord: word(11, "ata"), toWord: baseWord},
        ];
        const table = renderExamples(examples, orphan);
        const rows = [...table.querySelectorAll("tbody tr")];

        expect(rows.map(row => row.children[0].textContent)).toEqual(["at", "be", "z"]);
        expect(rows[0].children[1].textContent).toBe("ata, atu");
        expect([...rows[0].children[1].querySelectorAll("a")].map(link => link.getAttribute("href")))
            .toEqual(["/test/word/q/ata", "/test/word/q/atu"]);
        expect(rows[0].children[2].textContent).toBe("father");
        expect(examples.map(ex => ex.fromWord.id)).toEqual([31, 12, 21, 11]);
    });

    it("keeps homonymous base words in separate rows", () => {
        const table = renderExamples([
            {fromWord: word(11, "ata"), toWord: {...word(10, "at"), gloss: "father"}},
            {fromWord: word(21, "atu"), toWord: {...word(20, "at"), gloss: "mother"}},
        ]);
        const rows = [...table.querySelectorAll("tbody tr")];

        expect(rows).toHaveLength(2);
        expect(rows.map(row => row.children[1].textContent)).toEqual(["ata", "atu"]);
        expect(rows.map(row => row.children[2].textContent)).toEqual(["father", "mother"]);
    });

    it("puts syllabographic base words last and sorts each group by displayed spelling", () => {
        const table = renderExamples([
            {fromWord: word(11, "za"), toWord: word(10, "z")},
            {fromWord: word(21, "^d^_DU3-aš"), toWord: {
                ...word(20, "^d^_DU3"), syllabogramSequence: {syllabograms: [
                    {type: "Determinative", text: "d"},
                    {type: "LogogramAlt", text: "DU3"},
                ]},
            }},
            {fromWord: word(31, "ata"), toWord: word(30, "at")},
            {fromWord: word(41, "_A-aš"), toWord: {
                ...word(40, "_A"), syllabogramSequence: {syllabograms: [{type: "LogogramAlt", text: "A"}]},
            }},
        ]);

        expect([...table.querySelectorAll("tbody tr")].map(row => row.children[0].textContent))
            .toEqual(["at", "z", "A", "dDU3"]);
    });

    it("puts syllabographic attested derivations last within their shared row", () => {
        const baseWord = word(10, "at");
        const table = renderExamples([
            {fromWord: {...word(11, "_DU3"), syllabogramSequence: {
                syllabograms: [{type: "LogogramAlt", text: "DU3"}],
            }}, toWord: baseWord},
            {fromWord: word(12, "za"), toWord: baseWord},
            {fromWord: {...word(13, "_A"), syllabogramSequence: {
                syllabograms: [{type: "LogogramAlt", text: "A"}],
            }}, toWord: baseWord},
            {fromWord: word(14, "ata"), toWord: baseWord},
        ]);

        expect(table.querySelector("tbody tr")?.children[1].textContent).toBe("ata, za, A, DU3");
    });

    it.each([false, true])("only merges derivations with identical steps (orphan: %s)", orphan => {
        const baseWord = word(10, "at");
        const table = renderExamples([
            {fromWord: word(11, "ate"), toWord: baseWord, expectedWord: "ata", wordBeforeRule: "ab", wordAfterRule: "ad"},
            {fromWord: word(12, "ati"), toWord: baseWord, expectedWord: "ata", wordBeforeRule: "ab", wordAfterRule: "ad"},
            {fromWord: word(13, "atu"), toWord: baseWord, expectedWord: "ato", wordBeforeRule: "ac", wordAfterRule: "ae"},
        ], orphan);
        const rows = table.querySelectorAll("tbody tr");

        expect(rows).toHaveLength(2);
        expect(rows[0].children[1].textContent).toBe("ate, ati");
        expect(rows[0].children[3].textContent).toBe("at > ab > ad > ata");
        expect(rows[0].children[4].textContent).toBe("ata");
        expect(rows[1].children[1].textContent).toBe("atu");
        expect(rows[1].children[3].textContent).toBe("at > ac > ae > ato");
        expect(rows[1].children[4].textContent).toBe("ato");
    });

    it("keeps multi-step derivations separate when only the final result differs", () => {
        const baseWord = word(10, "at");
        const table = renderExamples([
            {fromWord: word(11, "ata"), toWord: baseWord, wordBeforeRule: "ab"},
            {fromWord: word(12, "atu"), toWord: baseWord, wordBeforeRule: "ab"},
        ]);
        const rows = [...table.querySelectorAll("tbody tr")];

        expect(rows.map(row => row.children[1].textContent)).toEqual(["ata", "atu"]);
        expect(rows.map(row => row.children[3].textContent)).toEqual(["at > ab > ata", "at > ab > atu"]);
    });

    it("keeps single-step examples separate from multi-step derivations of the same word", () => {
        const baseWord = word(10, "at");
        const table = renderExamples([
            {fromWord: word(11, "ata"), toWord: baseWord},
            {fromWord: word(12, "ati"), toWord: baseWord, wordBeforeRule: "ab", expectedWord: "ata"},
            {fromWord: word(13, "atu"), toWord: baseWord, wordBeforeRule: null, wordAfterRule: null},
        ]);
        const rows = [...table.querySelectorAll("tbody tr")];

        expect(rows.map(row => row.children[1].textContent)).toEqual(["ata, atu", "ati"]);
        expect(rows.map(row => row.children[3].textContent)).toEqual(["", "at > ab > ata"]);
    });

    it.each([false, true])("renders syllabograms in examples (orphan: %s)", orphan => {
        const table = renderExamples([{
            fromWord: word(1, "^d^_DU3-iš"), toWord: word(2, "^d^_DU3"),
            expectedWord: "^d^_DU3-aš", wordBeforeRule: null, wordAfterRule: null,
            expectedWordSyllabogramSequence: {syllabograms: [
                {type: "Determinative", text: "d"},
                {type: "LogogramAlt", text: "DU3"},
                {type: "Syllabogram", text: "aš"},
            ]},
        }], orphan);

        expect(table.querySelector("thead th:last-child")?.textContent).toBe("Expected");
        const expected = table.querySelector("tbody td:last-child")!;
        expect(expected.textContent).toBe("dDU3-aš");
        expect(expected.querySelector("sup")?.textContent).toBe("d");
        expect(expected.querySelector(".logogram i")?.textContent).toBe("DU3");
        expect(expected.querySelector("sub")?.textContent).toBe("3");
        expect(expected.querySelector(".wordText")?.textContent).toBe("aš");
    });

    it("keeps ordinary expected words and leaves matching examples blank", () => {
        const table = renderExamples([
            {fromWord: word(1, "ate"), toWord: word(2, "at"), expectedWord: "ata"},
            {fromWord: word(3, "ata"), toWord: word(4, "at")},
            {fromWord: word(5, "ata"), toWord: word(6, "at"), expectedWord: "ata"},
        ]);

        expect([...table.querySelectorAll("tbody td:last-child")].map(cell => cell.textContent))
            .toEqual(["ata", "", ""]);
        expect(table.querySelector("tbody td:last-child .syllabogram")).toBeNull();
    });

    it("hides the Expected column when all examples match", () => {
        const table = renderExamples([{fromWord: word(1, "ata"), toWord: word(2, "at")}]);
        expect(table.querySelector("thead")?.textContent).not.toContain("Expected");
    });
});
