// @vitest-environment jsdom
import {act, ReactNode} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import OpenCompoundModal from "@/components/OpenCompoundModal";
import OpenCompoundEntry from "@/components/OpenCompoundEntry";
import {AuthContext, GlobalStateContext, GraphContext} from "@/components/Contexts";
import {OpenCompoundViewModel, WordViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test"}, asPath: "/test/word/q/head", replace: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));
HTMLElement.prototype.scrollIntoView = vi.fn();

const head = {id: 10, text: "head", language: "q"} as WordViewModel;
const compound: OpenCompoundViewModel = {
    id: 30,
    components: [
        {id: 20, text: "component", language: "q", displayLanguage: "q", gloss: "modifier", homonym: false, reconstructed: false},
        {id: 10, text: "head", language: "q", displayLanguage: "q", gloss: "head", homonym: false, reconstructed: false}
    ],
    headIndex: 1, gloss: "Old gloss", fullGloss: "Old full gloss",
    source: [{refText: "REF:12"}], sourceEditableText: "REF:12", notes: "Old notes", attestations: []
};
let root: Root;
let container: HTMLDivElement;
let onCreated = vi.fn<() => void>();

beforeEach(() => {
    router.replace.mockReset();
    vi.stubGlobal("ResizeObserver", class {
        observe() {}
        unobserve() {}
        disconnect() {}
    });
    vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) => {
        if (options?.method === "POST") return new Response("{}", {
            status: 200, headers: {"Content-Type": "application/json"}
        });
        return new Response(JSON.stringify({words: [
            {ref: head},
            {ref: {id: 20, text: "component", language: "q", gloss: "modifier"}}
        ]}), {status: 200});
    }));
});

afterEach(async () => {
    if (root) await act(() => root.unmount());
    container?.remove();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

async function renderContent(content: ReactNode, canEdit = true) {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(() => root.render(
        <MantineProvider env="test">
            <GraphContext.Provider value="test">
            <AuthContext.Provider value={{authStatus: {
                authEnabled: false, authenticated: false, editableGraphs: canEdit ? ["test"] : []
            }}}>
            <GlobalStateContext.Provider value={{
                languages: [{shortName: "q", name: "Quenya", pos: [], grammaticalCategories: [],
                    wordClasses: [], dictionaries: []}],
                graphs: [], rules: [], publications: [{id: 1, name: "Reference", refId: "REF"}],
                inputAssists: {graphemes: []}
            }}>
                {content}
            </GlobalStateContext.Provider>
            </AuthContext.Provider>
            </GraphContext.Provider>
        </MantineProvider>
    ));
}

async function renderModal() {
    onCreated = vi.fn();
    await renderContent(<OpenCompoundModal opened={true} onClose={vi.fn()} headWord={head} onSaved={onCreated}/>);
}

async function selectWord() {
    await act(() => {
        document.querySelector<HTMLButtonElement>(".wordPickerItem")!.click();
    });
}

async function fillField(label: string, value: string) {
    const labelElement = Array.from(document.querySelectorAll("label"))
        .find(l => l.textContent === label || l.textContent === `${label}:`)!;
    const field = document.getElementById(labelElement.htmlFor)!;
    await act(() => {
        const prototype = field.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(prototype, "value")!.set!.call(field, value);
        field.dispatchEvent(new Event("input", {bubbles: true}));
    });
}

async function submit() {
    await act(() => { document.querySelector<HTMLButtonElement>('button[type="submit"]')!.click(); });
}

function postedRequest() {
    return vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === "POST");
}

it("requires a selection and creates a prefix while keeping the displayed word as head", async () => {
    await renderModal();
    expect(document.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    expect(Array.from(document.querySelectorAll(".wordPickerItem")).map(b => b.textContent))
        .toEqual(["component 'modifier'"]);
    await selectWord();
    await fillField("Gloss", "compound gloss");
    await fillField("Full gloss", "Full compound gloss");
    await fillField("Source", "RE");
    const suggestion = document.querySelector<HTMLButtonElement>(".sourceSuggestion")!;
    expect(suggestion.textContent).toBe("REF");
    await act(() => { suggestion.dispatchEvent(new MouseEvent("mousedown", {bubbles: true})); });
    expect(document.querySelector<HTMLInputElement>("#source")!.value).toBe("REF:");
    expect(document.querySelector(".sourceSuggestion")).toBeNull();
    expect(postedRequest()).toBeUndefined();
    await fillField("Source", "REF:42");
    await fillField("Notes", "Notes");
    await submit();

    const [url, options] = postedRequest()!;
    expect(url).toContain("test/compound/open");
    expect(options.credentials).toBe("include");
    expect(JSON.parse(options.body as string)).toEqual({
        componentIds: [20, 10], headIndex: 1,
        gloss: "compound gloss", fullGloss: "Full compound gloss", source: "REF:42", notes: "Notes"
    });
    expect(onCreated).toHaveBeenCalledOnce();
});

it("creates a suffix after the displayed head word", async () => {
    await renderModal();
    await selectWord();
    await act(() => { document.querySelector<HTMLInputElement>('input[type="radio"][value="suffix"]')!.click(); });
    await submit();

    expect(JSON.parse(postedRequest()![1].body as string)).toMatchObject({
        componentIds: [10, 20], headIndex: 0
    });
    expect(onCreated).toHaveBeenCalledOnce();
});

it("keeps the dialog open with the entered values when saving fails", async () => {
    await renderModal();
    await selectWord();
    await fillField("Gloss", "Keep this gloss");
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({message: "Cannot create compound"}), {
        status: 400, headers: {"Content-Type": "application/json"}
    }));
    await submit();

    expect(document.querySelector('[role="alert"]')!.textContent).toBe("Cannot create compound");
    expect(document.querySelector<HTMLInputElement>('input[value="Keep this gloss"]')).not.toBeNull();
    expect(onCreated).not.toHaveBeenCalled();
    expect(document.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
});

it("edits all four metadata fields while preserving the components and head", async () => {
    await renderContent(<OpenCompoundEntry compound={compound} word={head}/>);
    await act(() => { document.querySelector<HTMLButtonElement>('[aria-label="Edit open compound"]')!.click(); });

    expect(document.querySelector<HTMLInputElement>('input[value="Old gloss"]')).not.toBeNull();
    expect(document.querySelector<HTMLInputElement>("#fullGloss")!.value).toBe("Old full gloss");
    expect(document.querySelector<HTMLInputElement>('input[value="REF:12"]')).not.toBeNull();
    expect(Array.from(document.querySelectorAll("textarea")).some(field => field.value === "Old notes")).toBe(true);
    expect(document.querySelector('[aria-label="Search"]')).toBeNull();
    expect(document.querySelector('input[type="radio"]')).toBeNull();
    expect(fetch).not.toHaveBeenCalled();

    await fillField("Gloss", "New gloss");
    await fillField("Full gloss", "New full gloss");
    await fillField("Source", "REF:42");
    await fillField("Notes", "New notes");
    await submit();

    expect(postedRequest()![0]).toContain("test/compound/open/30");
    expect(JSON.parse(postedRequest()![1].body as string)).toEqual({
        componentIds: [20, 10], headIndex: 1,
        gloss: "New gloss", fullGloss: "New full gloss", source: "REF:42", notes: "New notes"
    });
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
});

it.each([false, true])("deletes an open compound only when confirmation is %s", async confirmed => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(confirmed);
    await renderContent(<OpenCompoundEntry compound={compound} word={head}/>);
    await act(() => { document.querySelector<HTMLButtonElement>('[aria-label="Delete open compound"]')!.click(); });

    expect(confirm).toHaveBeenCalledOnce();
    if (confirmed) {
        expect(postedRequest()![0]).toContain("test/compound/open/30/delete");
        expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
    }
    else {
        expect(fetch).not.toHaveBeenCalled();
        expect(router.replace).not.toHaveBeenCalled();
    }
});

it("shows a deletion error without refreshing the page", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await renderContent(<OpenCompoundEntry compound={compound} word={head}/>);
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({message: "Cannot delete compound"}), {
        status: 400, headers: {"Content-Type": "application/json"}
    }));
    await act(() => { document.querySelector<HTMLButtonElement>('[aria-label="Delete open compound"]')!.click(); });

    expect(document.querySelector('[role="alert"]')!.textContent).toBe("Cannot delete compound");
    expect(router.replace).not.toHaveBeenCalled();
});

it("hides editing actions without write access", async () => {
    await renderContent(<OpenCompoundEntry compound={compound} word={head}/>, false);
    expect(document.querySelector('[aria-label="Edit open compound"]')).toBeNull();
    expect(document.querySelector('[aria-label="Delete open compound"]')).toBeNull();
});

it("shows links to attested corpus texts beside an open compound", async () => {
    await renderContent(<OpenCompoundEntry compound={{...compound, attestations: [
        {textId: 42, textTitle: "First corpus text", lineNumbers: ["2", "4"]},
        {textId: 43, textTitle: "Second corpus text", lineNumbers: []}
    ]}} word={head}/>, false);

    expect(container.querySelector('a[href="/test/corpus/text/42"]')!.textContent).toBe("First corpus text");
    expect(container.querySelector('a[href="/test/corpus/text/43"]')!.textContent).toBe("Second corpus text");
    expect(container.textContent).toContain("in First corpus text 2, 4, Second corpus text");
});
