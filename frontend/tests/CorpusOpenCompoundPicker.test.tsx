// @vitest-environment jsdom
import {act, ReactNode} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import CorpusOpenCompoundPicker from "@/components/CorpusOpenCompoundPicker";
import {GlossDropdown} from "@/pages/[graph]/corpus/text/[id]";
import {AuthContext, GraphContext} from "@/components/Contexts";
import {CorpusTextViewModel, CorpusWordViewModel, OpenCompoundViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test"}, asPath: "/test/corpus/text/42", replace: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));

const compound: OpenCompoundViewModel = {
    id: 30,
    components: [
        {id: 10, text: "golden", language: "q", displayLanguage: "q", homonym: false, reconstructed: false},
        {id: 20, text: "leaves", language: "q", displayLanguage: "q", homonym: false, reconstructed: false}
    ],
    headIndex: 1, gloss: "foliage", source: [], sourceEditableText: "", attestations: []
};
let root: Root;
let container: HTMLDivElement;
let onClose = vi.fn<() => void>();

function jsonResponse(data: unknown, status = 200) {
    return new Response(JSON.stringify(data), {status, headers: {"Content-Type": "application/json"}});
}

beforeEach(() => {
    router.replace.mockReset();
    onClose = vi.fn();
    vi.stubGlobal("ResizeObserver", class {
        observe() {}
        unobserve() {}
        disconnect() {}
    });
    vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) =>
        options?.method === "POST" ? jsonResponse({}) : jsonResponse([compound])
    ));
});

afterEach(async () => {
    if (root) await act(() => root.unmount());
    container?.remove();
    vi.unstubAllGlobals();
});

async function renderContent(content: ReactNode) {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(() => root.render(<MantineProvider env="test">
        <GraphContext.Provider value="test">
        <AuthContext.Provider value={{authStatus: {
            authEnabled: false, authenticated: false, editableGraphs: ["test"]
        }}}>
            {content}
        </AuthContext.Provider>
        </GraphContext.Provider>
    </MantineProvider>));
}

async function renderPicker() {
    await renderContent(<CorpusOpenCompoundPicker corpusTextId={42} index={5} onClose={onClose}/>);
}

async function renderMenu(openCompoundId?: number) {
    const word: CorpusWordViewModel = {
        index: 5, text: "leaves", normalizedText: "leaves", gloss: "foliage",
        glossable: true, homonym: false, openCompoundId
    };
    await renderContent(<GlossDropdown corpusText={{id: 42} as CorpusTextViewModel} word={word}
        showDefineWordForm={vi.fn()} showDefineLemmaForm={vi.fn()} showContextGlossForm={vi.fn()}
        showTranslationFormAtWord={vi.fn()}/>);
    await act(() => { document.querySelector<HTMLElement>(".glossChoice")!.click(); });
}

function menuButton(label: string) {
    return Array.from(document.querySelectorAll<HTMLButtonElement>("button"))
        .find(button => button.textContent === label);
}

it("loads backend candidates and shows full component words and gloss", async () => {
    await renderPicker();
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain("test/corpus/text/42/openCompounds/5");
    expect(document.querySelector(".wordPickerItem")!.textContent).toBe("golden leaves 'foliage'");
});

it("links the selected candidate using the backend and refreshes the text", async () => {
    await renderPicker();
    await act(() => { document.querySelector<HTMLButtonElement>(".wordPickerItem")!.click(); });

    const [url, options] = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(url).toContain("test/corpus/text/42/openCompound");
    expect(JSON.parse(options.body as string)).toEqual({index: 5, compoundId: 30});
    expect(options.credentials).toBe("include");
    expect(onClose).toHaveBeenCalledOnce();
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
});

it("shows an empty state when no candidates are available", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([]));
    await renderPicker();
    expect(document.body.textContent).toContain("No matching open compounds");
    expect(document.querySelector(".wordPickerItem")).toBeNull();
});

it("shows candidate loading failures", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({message: "Cannot load candidates"}, 400));
    await renderPicker();
    expect(document.querySelector('[role="alert"]')!.textContent).toBe("Cannot load candidates");
    expect(document.querySelector(".wordPickerItem")).toBeNull();
});

it("keeps the popup open when a candidate is no longer available", async () => {
    await renderPicker();
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({message: "Candidate no longer available"}, 400));
    await act(() => { document.querySelector<HTMLButtonElement>(".wordPickerItem")!.click(); });

    expect(document.querySelector('[role="alert"]')!.textContent).toBe("Candidate no longer available");
    expect(onClose).not.toHaveBeenCalled();
    expect(router.replace).not.toHaveBeenCalled();
    expect(document.querySelector<HTMLButtonElement>(".wordPickerItem")!.disabled).toBe(false);
});

it("offers linking for an unlinked word", async () => {
    await renderMenu();
    expect(menuButton("Link to open compound")).toBeDefined();
    expect(menuButton("Remove open compound link")).toBeUndefined();
    await act(() => { menuButton("Link to open compound")!.click(); });
    expect(document.querySelector(".wordPickerItem")!.textContent).toBe("golden leaves 'foliage'");
});

it("offers removal instead of linking for a linked word and refreshes after unlinking", async () => {
    await renderMenu(30);
    expect(menuButton("Link to open compound")).toBeUndefined();
    expect(menuButton("Remove open compound link")).toBeDefined();
    await act(() => { menuButton("Remove open compound link")!.click(); });

    const [url, options] = vi.mocked(fetch).mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(url).toContain("test/corpus/text/42/openCompound/remove");
    expect(JSON.parse(options.body as string)).toEqual({index: 5, compoundId: 30});
    expect(options.credentials).toBe("include");
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
});

it("keeps the removal menu open and shows errors when unlinking fails", async () => {
    await renderMenu(30);
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({message: "The word is no longer linked"}, 400));
    await act(() => { menuButton("Remove open compound link")!.click(); });

    expect(document.querySelector('[role="alert"]')!.textContent).toBe("The word is no longer linked");
    expect(menuButton("Remove open compound link")!.disabled).toBe(false);
    expect(router.replace).not.toHaveBeenCalled();
});
