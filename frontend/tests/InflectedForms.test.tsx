// @vitest-environment jsdom
import {renderToStaticMarkup} from "react-dom/server";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import {WordLinkTypeComponent} from "@/pages/[graph]/word/[lang]/[...text]";
import {AuthContext, GlobalStateContext, GraphContext} from "@/components/Contexts";
import {LinkTypeViewModel, LinkWordViewModel, WordViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test"}, asPath: "/test/word/q/elen", replace: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root: Root;
let editableContainer: HTMLDivElement;

afterEach(async () => {
    if (root) await act(() => root.unmount());
    root = undefined;
    editableContainer?.remove();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
});

const word = {id: 1, text: "elen", language: "q"} as WordViewModel;
const form: LinkWordViewModel = {
    word: {id: 2, text: "elena", language: "q", displayLanguage: "q", gloss: "star.ACC",
        homonym: false, reconstructed: false},
    ruleIds: [10], ruleNames: ["q-acc"], ruleResults: [],
    source: [], sourceEditableText: "", suggestedSequences: [],
    paradigm: {id: 20, name: "Nouns"},
    attestations: [{textId: 30, textTitle: "Sample text", lineNumbers: ["1", "2a"], word: "elenna"}]
};

function renderLinks(words: LinkWordViewModel[], {
    directionFrom = false, excludeIds = [], canEdit = false, languageSyllabographic = false
} = {}) {
    const links: LinkTypeViewModel[] = [{typeId: ">", type: "Inflected forms", words}];
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(
        <GraphContext.Provider value="test">
            <AuthContext.Provider value={{authStatus: {
                authEnabled: false, authenticated: false, editableGraphs: canEdit ? ["test"] : []
            }}}>
                <WordLinkTypeComponent word={{...word, languageSyllabographic}} links={links}
                                       directionFrom={directionFrom} excludeIds={excludeIds}/>
            </AuthContext.Provider>
        </GraphContext.Provider>
    );
    return container;
}

it("shows rule, form, and attestations in separate columns with paradigm groups", () => {
    const container = renderLinks([
        form,
        {...form, word: {...form.word, id: 6, text: "elenna"}, attestations: []},
        {...form, word: {...form.word, id: 3, text: "eleni"}, attestations: [], ruleIds: [11, 12],
            ruleNames: ["q-pl", "q-variant"]},
        {...form, word: {...form.word, id: 7}, attestations: []},
        {...form, word: {...form.word, id: 4}, paradigm: {id: 21, name: "Adjectives"}},
        {...form, word: {...form.word, id: 5}, paradigm: null, ruleIds: [], ruleNames: [], attestations: []}
    ]);
    expect(container.querySelector("caption")?.textContent).toBe("Inflected forms");
    expect([...container.querySelectorAll("thead th")].map(th => th.textContent))
        .toEqual(["Rule", "Form", "Attestations"]);
    expect([...container.querySelectorAll("tbody th")].map(th => th.textContent))
        .toEqual(["Nouns", "Adjectives", "Other forms"]);
    const rows = [...container.querySelectorAll("tbody tr")].filter(tr => tr.querySelector("td"));
    expect(rows).toHaveLength(6);
    expect([...rows[0].querySelectorAll("td")].map(td => td.textContent))
        .toEqual(["q-acc", "elena", "Sample text 1, 2a (elenna)"]);
    expect(rows[0].querySelector('td:nth-child(1) a')?.getAttribute("href")).toBe("/test/rule/10");
    expect(rows[0].querySelector('td:nth-child(2) a')?.getAttribute("href")).toBe("/test/word/q/elena");
    expect(rows[0].querySelector('td:nth-child(3) a')?.getAttribute("href")).toBe("/test/corpus/text/30");
    expect(rows[0].querySelector("td")?.getAttribute("rowspan")).toBe("2");
    expect(rows.map(row => row.querySelectorAll("td").length)).toEqual([3, 2, 3, 3, 3, 3]);
    expect(rows[1].querySelector("td")?.textContent).toBe("elenna");
    expect(rows[1].lastElementChild?.textContent).toBe("");
    expect(rows.slice(2).map(row => row.querySelector("td")?.textContent))
        .toEqual(["q-pl, q-variant", "q-acc", "q-acc", ""]);
    expect(container.querySelector("button")).toBeNull();
});

it("preserves link metadata and editing actions in the form column", () => {
    const container = renderLinks([{...form, notes: "Variant spelling", source: [{refText: "REF:42"}]}], {canEdit: true});
    const cell = container.querySelector("tbody td:nth-child(2)")!;
    expect(cell.textContent).toContain("Variant spelling");
    expect(cell.textContent).toContain("REF:42");
    expect([...cell.querySelectorAll("button")].map(button => button.getAttribute("aria-label")))
        .toEqual(["Edit inflected form", "Delete inflected form"]);
    expect(cell.querySelectorAll("button.hoverColorIcon svg")).toHaveLength(2);
    expect([...cell.querySelectorAll("button")].map(button => button.textContent)).toEqual(["", ""]);
});

it("groups syllabographic attestations into distinct transliteration rows with their matching texts", () => {
    const syllabograms = {syllabograms: [
        {type: "Determinative" as const, text: "d"}, {type: "LogogramAlt" as const, text: "DU3"}
    ]};
    const container = renderLinks([
        {...form, attestations: [
            {textId: 30, textTitle: "First text", word: "e-le-na", lineNumbers: ["1", "2"]},
            {textId: 30, textTitle: "First text", word: "^d^_DU3", syllabogramSequence: syllabograms, lineNumbers: ["3"]},
            {textId: 31, textTitle: "Second text", word: "e-le-na", lineNumbers: ["5"]},
            {textId: 30, textTitle: "First text", word: "e-le-na", lineNumbers: ["2", "4"]},
            {textId: 32, textTitle: "Third text", word: null, lineNumbers: []}
        ]},
        {...form, word: {...form.word, id: 3, text: "eleni"}, attestations: []}
    ], {languageSyllabographic: true, canEdit: true});
    expect([...container.querySelectorAll("thead th")].map(th => th.textContent))
        .toEqual(["Rule", "Form", "Transliteration", "Attestations"]);
    expect(container.querySelector("tbody th")?.getAttribute("colspan")).toBe("4");
    const rows = [...container.querySelectorAll("tbody tr")].filter(tr => tr.querySelector("td"));
    expect(rows).toHaveLength(4);
    expect(rows[0].querySelector("td")?.getAttribute("rowspan")).toBe("4");
    expect(rows.map(row => row.querySelectorAll("td").length)).toEqual([4, 2, 2, 3]);
    expect(rows[0].querySelector("td:nth-child(2)")?.getAttribute("rowspan")).toBe("3");
    expect(rows.map(row => row.lastElementChild?.previousElementSibling?.textContent))
        .toEqual(["e-le-na", "dDU3", "elena", ""]);
    expect(rows.map(row => row.lastElementChild?.textContent))
        .toEqual(["First text 1, 2, 4, Second text 5", "First text 3", "Third text", ""]);
    const logogram = rows[1].firstElementChild!;
    expect(logogram.querySelector("sup")?.textContent).toBe("d");
    expect(logogram.querySelector("i")?.textContent).toBe("DU3");
    expect(logogram.querySelector("sub")?.textContent).toBe("3");
    expect(container.querySelectorAll('a[href="/test/corpus/text/30"]')).toHaveLength(2);
    expect([...container.querySelectorAll("button")].map(button => button.getAttribute("aria-label")))
        .toEqual(["Edit inflected form", "Delete inflected form", "Edit inflected form", "Delete inflected form"]);
});

it("hides excluded forms and keeps the base word link outside the table", () => {
    expect(renderLinks([form], {excludeIds: [2]}).querySelector("table")).toBeNull();
    expect(renderLinks([]).querySelector("table")).toBeNull();
    const container = renderLinks([form], {directionFrom: true});
    expect(container.querySelector("table")).toBeNull();
    expect(container.querySelector('a[href="/test/rule/10"]')?.textContent).toBe("q-acc");
});

async function openEditPopup() {
    window.matchMedia = vi.fn().mockImplementation((media: string) => ({
        media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()
    }));
    vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, {status: 200})));
    editableContainer = document.createElement("div");
    document.body.appendChild(editableContainer);
    root = createRoot(editableContainer);
    await act(() => root.render(<MantineProvider env="test">
        <GraphContext.Provider value="test">
            <AuthContext.Provider value={{authStatus: {
                authEnabled: false, authenticated: false, editableGraphs: ["test"]
            }}}>
                <GlobalStateContext.Provider value={{graphs: [], languages: [], publications: [],
                    inputAssists: {graphemes: []}, rules: [
                        {id: 10, name: "q-acc", toLang: "q", fromPOS: [], summaryText: "", optional: false, dispreferred: false}
                    ]}}>
                    <WordLinkTypeComponent word={word} directionFrom={false} links={[{
                        typeId: ">", type: "Inflected forms",
                        words: [{...form, sourceEditableText: "REF:42", notes: "Old notes"}]
                    }]}/>
                </GlobalStateContext.Provider>
            </AuthContext.Provider>
        </GraphContext.Provider>
    </MantineProvider>));
    await act(() => {
        editableContainer.querySelector<HTMLButtonElement>('[aria-label="Edit inflected form"]')!.click();
    });
    return document.querySelector<HTMLElement>('[role="dialog"]')!;
}

it("opens the inflected form editor in a popup and cancels without saving", async () => {
    const dialog = await openEditPopup();
    expect(dialog).not.toBeNull();
    expect(dialog.textContent).toContain("Edit inflected form");
    expect(editableContainer.querySelector("form")?.closest('[role="dialog"]')).toBe(dialog);
    expect(dialog.querySelector<HTMLInputElement>("#source")!.value).toBe("REF:42");
    expect(dialog.querySelector<HTMLInputElement>("#notes")!.value).toBe("Old notes");
    expect(dialog.textContent).toContain("q-acc");
    await act(() => {
        [...dialog.querySelectorAll("button")].find(button => button.textContent === "Cancel")!.click();
    });
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
    expect(router.replace).not.toHaveBeenCalled();
});

it("saves the derivation from the popup, closes it, and refreshes the word page", async () => {
    const dialog = await openEditPopup();
    await act(() => {
        const notes = dialog.querySelector<HTMLInputElement>("#notes")!;
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(notes, "Updated notes");
        notes.dispatchEvent(new Event("input", {bubbles: true}));
    });
    await act(() => { dialog.querySelector<HTMLInputElement>('input[type="submit"]')!.click(); });
    expect(fetch).toHaveBeenCalledOnce();
    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(url).toContain("test/link/update");
    expect(JSON.parse(options.body as string)).toEqual({
        fromEntity: 1, toEntity: 2, linkType: ">", ruleNames: "q-acc", source: "REF:42", notes: "Updated notes"
    });
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
});
