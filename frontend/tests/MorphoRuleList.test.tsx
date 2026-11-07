// @vitest-environment jsdom
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import MorphoRuleList from "@/pages/[graph]/rules/[lang]/morpho";
import {AuthContext, GlobalStateContext, GraphContext} from "@/components/Contexts";
import {ParadigmViewModel, RuleListViewModel} from "@/models";

const {router} = vi.hoisted(() => ({router: {
    query: {graph: "test", lang: "q"}, asPath: "/test/rules/q/morpho#paradigm-7",
    replace: vi.fn(), push: vi.fn()
}}));
vi.mock("next/router", () => ({useRouter: () => router}));
vi.mock("@/components/Breadcrumbs", () => ({default: () => null}));
vi.mock("@/components/LanguageNavBar", () => ({default: () => null}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));

const paradigm: ParadigmViewModel = {
    id: 7, name: "Nouns", language: "q", languageFullName: "Quenya", pos: ["N"],
    rowTitles: ["Nom", "Acc"],
    columns: [{title: "Sg", cells: [
        {alternativeRuleIds: [null, 10], alternativeRuleNames: [".", "q-nom"], alternativeRuleSummaries: ["", "-a"]},
        {alternativeRuleIds: [11, 12], alternativeRuleNames: ["q-acc", "q-acc-alt"], alternativeRuleSummaries: ["", "-n"]},
    ]}],
    editableText: "Sg\nNom .|q-nom\nAcc q-acc|q-acc-alt", preRule: {id: 13, name: "q-stem"}, postRule: null,
};

function ruleList(table = paradigm): RuleListViewModel {
    return {toLangFullName: "Quenya", ruleGroups: [
        {groupName: `Grammar: ${table.name}`, rules: [], paradigm: table},
        {groupName: "Grammar: Other", rules: [{id: 20, name: "q-other", toLang: "q", fromPOS: [], summaryText: "-s", optional: false, dispreferred: false}]},
    ]};
}

let root: Root;
let container: HTMLDivElement;
let latestRules: RuleListViewModel;

function jsonResponse(data: unknown, status = 200) {
    return new Response(JSON.stringify(data), {status, headers: {"Content-Type": "application/json"}});
}

beforeEach(() => {
    vi.clearAllMocks();
    latestRules = ruleList();
    vi.stubEnv("NEXT_PUBLIC_BACKEND_URL", "http://backend/");
    vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
    vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) =>
        options?.method === "POST" ? new Response(null, {status: 200}) : jsonResponse(latestRules)
    ));
});

afterEach(async () => {
    if (root) await act(() => root.unmount());
    container?.remove();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
});

async function renderPage(canEdit = true) {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(() => root.render(<MantineProvider env="test">
        <GraphContext.Provider value="test">
            <AuthContext.Provider value={{authStatus: {authEnabled: false, authenticated: false, editableGraphs: canEdit ? ["test"] : []}}}>
                <GlobalStateContext.Provider value={{graphs: [], languages: [], rules: [], publications: [], inputAssists: {graphemes: []}}}>
                    <MorphoRuleList loaderData={ruleList()}/>
                </GlobalStateContext.Provider>
            </AuthContext.Provider>
        </GraphContext.Provider>
    </MantineProvider>));
}

function button(label: string) {
    return Array.from(document.querySelectorAll<HTMLButtonElement>("button")).find(b => b.textContent === label)!;
}

it("renders anchored paradigm tables with correctly aligned rule alternatives and keeps other rule lists", async () => {
    await renderPage();
    const section = container.querySelector("#paradigm-7")!.parentElement!;
    expect(section.querySelector("thead")!.textContent).toBe("Sg");
    expect([...section.querySelectorAll("tbody th")].map(th => th.textContent)).toEqual(["Nom", "Acc"]);
    expect([...section.querySelectorAll("tbody a")].map(a => [a.getAttribute("href"), a.textContent]))
        .toEqual([["/test/rule/10", "-a"], ["/test/rule/11", "q-acc"], ["/test/rule/12", "-n"]]);
    expect(section.querySelector("tbody td")!.textContent).toBe(".\u00a0|\u00a0-a");
    expect(section.textContent).toContain("Pre rule: q-stem");
    expect(container.querySelector('a[href="/test/rule/20"]')!.textContent).toBe("q-other");
    expect(button("Edit Paradigm")).toBeDefined();
    expect(button("Delete Paradigm")).toBeDefined();
});

it("hides paradigm editing controls for read-only users", async () => {
    await renderPage(false);
    expect(container.querySelector("#paradigm-7")).not.toBeNull();
    expect(button("Edit Paradigm")).toBeUndefined();
    expect(button("Delete Paradigm")).toBeUndefined();
});

it("edits and refreshes the inline paradigm while visiting an anchor", async () => {
    await renderPage();
    await act(() => button("Edit Paradigm").click());
    const field = container.querySelector<HTMLInputElement>("#name")!;
    expect(field.value).toBe("Nouns");
    expect(container.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe(paradigm.editableText);
    await act(() => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(field, "New nouns");
        field.dispatchEvent(new Event("input", {bubbles: true}));
    });
    latestRules = ruleList({...paradigm, name: "New nouns"});
    await act(() => container.querySelector("form")!.requestSubmit());

    expect(fetch).toHaveBeenCalledWith("http://backend/test/paradigm/7", expect.objectContaining({
        method: "POST", body: JSON.stringify({name: "New nouns", pos: "N", text: paradigm.editableText, preRuleName: "q-stem"}),
    }));
    expect(fetch).toHaveBeenCalledWith("http://backend/test/rules/q/morpho", expect.anything());
    expect(container.querySelector("#paradigm-7")!.textContent).toBe("Grammar: New nouns");
    expect(container.querySelector("form")).toBeNull();
    expect(button("Edit Paradigm")).toBeDefined();
});

it("cancels editing without saving", async () => {
    await renderPage();
    await act(() => button("Edit Paradigm").click());
    await act(() => button("Cancel").click());
    expect(container.querySelector("form")).toBeNull();
    expect(button("Edit Paradigm")).toBeDefined();
    expect(fetch).not.toHaveBeenCalled();
});

it("confirms deletion and refreshes the list without the deleted paradigm", async () => {
    await renderPage();
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    await act(() => button("Delete Paradigm").click());
    expect(fetch).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    latestRules = {toLangFullName: "Quenya", ruleGroups: []};
    await act(() => button("Delete Paradigm").click());
    expect(fetch).toHaveBeenCalledWith("http://backend/test/paradigm/7/delete", expect.objectContaining({method: "POST"}));
    expect(container.querySelector("#paradigm-7")).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/test/rules/q/morpho", undefined, {scroll: false});
});

it("keeps the table and reports a failed deletion", async () => {
    await renderPage();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({message: "Cannot delete paradigm"}, 400));
    await act(() => button("Delete Paradigm").click());
    expect(container.querySelector(".errorText")!.textContent).toBe("Cannot delete paradigm");
    expect(container.querySelector("#paradigm-7")).not.toBeNull();
    expect(router.replace).not.toHaveBeenCalled();
});

it("refreshes generated paradigms before navigating to their anchor on the same page", async () => {
    await renderPage();
    await act(() => button("Generate Paradigm").click());
    const generated = {...paradigm, id: 8, name: "Generated"};
    latestRules = ruleList(generated);
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(generated));
    await act(() => document.querySelector("form")!.requestSubmit());
    expect(fetch).toHaveBeenCalledWith("http://backend/test/paradigm/generate", expect.objectContaining({method: "POST"}));
    expect(container.querySelector("#paradigm-8")!.textContent).toBe("Grammar: Generated");
    expect(router.push).toHaveBeenCalledWith("/test/rules/q/morpho#paradigm-8");
});
