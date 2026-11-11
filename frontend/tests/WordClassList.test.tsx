// @vitest-environment jsdom
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import WordClassList from "@/components/WordClassList";
import {AuthContext, GraphContext} from "@/components/Contexts";
import {WordCategoryViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test"}, asPath: "/test/language/q", replace: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const categories: WordCategoryViewModel[] = [
    {name: "Plural type", pos: ["N"], values: [
        {name: "class-plural-rim", abbreviation: "class-plural-rim"},
        {name: "class-plural-li", abbreviation: "class-plural-li"}
    ]},
    {name: "Gender", pos: ["N", "ADJ"], values: [{name: "Animate", abbreviation: "AN"}]}
];
let root: Root;
let container: HTMLDivElement;

beforeEach(() => {
    router.replace.mockReset();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, {status: 200})));
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});

afterEach(async () => {
    await act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
});

async function renderList(editable = true) {
    await act(() => root.render(<GraphContext.Provider value="test">
        <AuthContext.Provider value={{authStatus: {
            authEnabled: false, authenticated: false, editableGraphs: editable ? ["test"] : []
        }}}>
            <WordClassList language="q" categories={categories}/>
        </AuthContext.Provider>
    </GraphContext.Provider>));
}

async function click(label: string) {
    const button = Array.from(container.querySelectorAll("button"))
        .find(button => button.getAttribute("aria-label") === label || button.textContent === label);
    expect(button).toBeDefined();
    await act(() => button!.click());
}

async function changeName(name: string) {
    const input = container.querySelector("input")!;
    await act(() => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, name);
        input.dispatchEvent(new Event("input", {bubbles: true}));
    });
}

it("opens the rename form beneath the selected word class and cancels without saving", async () => {
    await renderList();
    expect(container.querySelectorAll("button.hoverColorIcon svg")).toHaveLength(3);
    await click("Edit Animate (AN)");

    const form = container.querySelector("form")!;
    expect(form.closest("li")!.textContent).toContain("Gender (N, ADJ)");
    expect(container.querySelector("input")!.value).toBe("Animate (AN)");
    expect(document.activeElement).toBe(container.querySelector("input"));
    await changeName("Inanimate (IN)");
    await click("Cancel");
    expect(container.querySelector("form")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();

    await click("Edit Animate (AN)");
    expect(container.querySelector("input")!.value).toBe("Animate (AN)");
});

it("renames the selected value and refreshes the language page", async () => {
    await renderList();
    await click("Edit class-plural-li");
    await changeName("class-plural-ron");
    await click("Save");

    const [url, options] = vi.mocked(fetch).mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toContain("test/language/q/renameWordClassValue");
    expect(JSON.parse(options.body as string)).toEqual({oldAbbreviation: "class-plural-li", newValue: "class-plural-ron"});
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
    expect(container.querySelector("form")).toBeNull();
});

it("keeps the editor open on errors and allows correcting the value", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({message: "Duplicate abbreviation"}), {status: 400}));
    await renderList();
    await click("Edit Animate (AN)");
    await changeName("First (I)");
    await click("Save");

    expect(container.querySelector('[role="alert"]')!.textContent).toBe("Duplicate abbreviation");
    expect(container.querySelector("input")!.value).toBe("First (I)");
    expect(router.replace).not.toHaveBeenCalled();
    await changeName("Inanimate (IN)");
    await click("Save");
    expect(container.querySelector("form")).toBeNull();
    expect(router.replace).toHaveBeenCalledExactlyOnceWith(router.asPath);
});

it("shows class values without edit buttons for readers", async () => {
    await renderList(false);
    expect(container.textContent).toContain("Plural type (N): class-plural-rim, class-plural-li");
    expect(container.textContent).toContain("Gender (N, ADJ): Animate (AN)");
    expect(container.querySelector("button")).toBeNull();
});
