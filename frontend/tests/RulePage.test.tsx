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

describe("rule examples Expected column", () => {
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
