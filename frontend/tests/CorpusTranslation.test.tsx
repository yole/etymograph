// @vitest-environment jsdom
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import CorpusText from "@/pages/[graph]/corpus/text/[id]";
import {AuthContext, GraphContext} from "@/components/Contexts";
import {CorpusTextViewModel, CorpusWordViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test", id: "42"}, asPath: "/test/corpus/text/42", replace: vi.fn(), push: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));

let root: Root;
let container: HTMLDivElement;

function word(index: number): CorpusWordViewModel {
    return {
        index, text: `word-${index}`, normalizedText: `word-${index}`, wordText: `word-${index}`,
        wordId: 100 + index, gloss: "gloss", glossable: true, homonym: false,
    };
}

const corpusText: CorpusTextViewModel = {
    id: 42, title: "Test text", language: "q", languageFullName: "Quenya", syllabographic: false,
    text: "word-0 word-1\nword-2 word-3", source: [], sourceEditableText: "", consistencyIssues: [],
    lines: [{words: [word(0), word(1)], separator: false}, {words: [word(2), word(3)], separator: false}],
    translations: [],
};

beforeEach(() => {
    router.replace.mockReset();
    router.push.mockReset();
    vi.stubGlobal("ResizeObserver", class {
        observe() {}
        unobserve() {}
        disconnect() {}
    });
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({}), {
        status: 200, headers: {"Content-Type": "application/json"},
    })));
});

afterEach(async () => {
    if (root) await act(() => root.unmount());
    container?.remove();
    vi.unstubAllGlobals();
});

async function renderText() {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(() => root.render(<MantineProvider env="test">
        <GraphContext.Provider value="test">
            <AuthContext.Provider value={{authStatus: {
                authEnabled: false, authenticated: false, editableGraphs: ["test"]
            }}}>
                <CorpusText loaderData={corpusText}/>
            </AuthContext.Provider>
        </GraphContext.Provider>
    </MantineProvider>));
}

function wordElement(index: number) {
    return container.querySelectorAll<HTMLElement>(".corpusTextWord")[index];
}

async function clickAction(label: string) {
    const action = Array.from(document.querySelectorAll<HTMLElement>(".inlineButton, button"))
        .find(element => element.textContent === label);
    expect(action).toBeDefined();
    await act(() => { action!.click(); });
}

async function startTranslation(index: number) {
    await act(() => { wordElement(index).querySelector<HTMLElement>(".glossChoice")!.click(); });
    await clickAction("Translate from here");
}

async function selectEnd(index: number) {
    await act(() => { wordElement(index).querySelector<HTMLButtonElement>("button")!.click(); });
}

async function saveTranslation() {
    const textarea = container.querySelector<HTMLTextAreaElement>("textarea#text")!;
    await act(() => {
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")!.set!.call(textarea, "Translation");
        textarea.dispatchEvent(new Event("input", {bubbles: true}));
    });
    await act(() => { textarea.closest("form")!.requestSubmit(); });
    const [url, options] = vi.mocked(fetch).mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toContain("test/translation");
    return JSON.parse(options.body as string);
}

it("shows the endpoint prompt and link buttons underneath the starting word", async () => {
    await renderText();
    await startTranslation(1);

    expect(wordElement(1).querySelector(".translationSelection")!.textContent)
        .toContain("Click last word to translate");
    expect(wordElement(1).querySelector(".translationSelection")!.textContent).toContain("Entire text");
    expect(wordElement(1).querySelector(".translationSelection")!.textContent).toContain("Cancel");
    expect(container.querySelector("textarea")).toBeNull();
    expect(container.querySelector(".corpusTextWord a")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
});

it.each([[1, 2, 1, 3], [1, 1, 1, 2], [2, 0, 0, 3]])(
    "saves the inclusive range from word %i to word %i and places the form under the endpoint",
    async (first, last, anchorStartIndex, anchorEndIndex) => {
        await renderText();
        await startTranslation(first);
        await selectEnd(last);

        expect(container.querySelector(".translationSelection")).toBeNull();
        expect(wordElement(last).querySelector("textarea#text")).not.toBeNull();
        expect(document.activeElement).toBe(wordElement(last).querySelector("textarea#text"));
        expect(container.querySelector(".corpusTextWord a")).not.toBeNull();
        expect(await saveTranslation()).toMatchObject({corpusTextId: 42, text: "Translation", anchorStartIndex, anchorEndIndex});
        expect(container.querySelector("textarea")).toBeNull();
        expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
        expect(router.push).not.toHaveBeenCalled();
    }
);

it("cancels selection and permits a fresh range", async () => {
    await renderText();
    await startTranslation(0);
    await clickAction("Cancel");
    expect(container.querySelector(".translationSelection")).toBeNull();
    expect(container.querySelector("textarea")).toBeNull();
    expect(container.querySelector(".corpusTextWord a")).not.toBeNull();
    expect(fetch).not.toHaveBeenCalled();

    await startTranslation(2);
    await selectEnd(3);
    expect(await saveTranslation()).toMatchObject({anchorStartIndex: 2, anchorEndIndex: 4});
});

it("creates an entire-text translation without word anchors", async () => {
    await renderText();
    await startTranslation(2);
    await clickAction("Entire text");

    expect(wordElement(2).querySelector("textarea#text")).not.toBeNull();
    const data = await saveTranslation();
    expect(data).toMatchObject({corpusTextId: 42, text: "Translation"});
    expect(data).not.toHaveProperty("anchorStartIndex");
    expect(data).not.toHaveProperty("anchorEndIndex");
});

it("cancels the translation form without saving", async () => {
    await renderText();
    await startTranslation(0);
    await selectEnd(1);
    await clickAction("Cancel");

    expect(container.querySelector("textarea")).toBeNull();
    expect(container.querySelector(".translationSelection")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
});
