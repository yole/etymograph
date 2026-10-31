// @vitest-environment jsdom
import {renderToStaticMarkup} from "react-dom/server";
import {describe, expect, it, vi} from "vitest";
import ConsistencyPage from "@/pages/[graph]/language/[lang]/check";
import {GraphContext} from "@/components/Contexts";
import {ConsistencyIssueGroupViewModel} from "@/models";

vi.mock("@/components/Breadcrumbs", () => ({default: () => null}));
vi.mock("@/components/LanguageNavBar", () => ({default: () => null}));

const plainText = {emph: false, subscript: false, monospaced: false};

function renderReport(issueGroups: ConsistencyIssueGroupViewModel[]) {
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<GraphContext.Provider value="test">
        <ConsistencyPage loaderData={{language: "q", languageFullName: "Quenya", issueGroups}}/>
    </GraphContext.Provider>);
    return container;
}

describe("consistency issue links", () => {
    it("renders syllabographic words with determinatives, logograms and sign numbers", () => {
        const link = {linkType: "word", linkId: 10, linkLanguage: "q", linkData: "d_DU3"};
        const container = renderReport([{type: "Parts of speech", issues: [{fragments: [
            {...plainText, text: "No POS specified for "},
            {...plainText, ...link, text: "^d^_DU3", syllabogramSequence: {syllabograms: [
                {type: "Determinative", text: "d"},
                {type: "LogogramAlt", text: "DU3"},
            ]}},
            {...plainText, text: " 'god'"},
        ]}]}]);

        const issue = container.querySelector("li")!;
        expect(issue.textContent).toBe("No POS specified for dDU3 'god'");
        expect(issue.querySelector("sup")?.textContent).toBe("d");
        expect(issue.querySelector(".logogram i")?.textContent).toBe("DU3");
        expect(issue.querySelector("sub")?.textContent).toBe("3");
        expect(issue.querySelectorAll("a")).toHaveLength(1);
        expect(issue.querySelector("a")?.getAttribute("href")).toBe("/test/word/q/d_DU3/10");
        expect(issue.querySelector("a")?.textContent).toBe("dDU3");
    });

    it("links word issues to the exact word, including homonyms and URL characters", () => {
        const container = renderReport([{type: "Parts of speech", issues: [
            {fragments: [
                {...plainText, text: "Missing POS for "},
                {...plainText, text: "a/b?#", linkType: "word", linkId: 10, linkLanguage: "q", linkData: "a/b?#"},
                {...plainText, text: " 'first word'"},
            ]},
            {fragments: [
                {...plainText, text: "Missing POS for "},
                {...plainText, text: "a/b?#", linkType: "word", linkId: 20, linkLanguage: "q", linkData: "a/b?#"},
                {...plainText, text: " 'second word'"},
            ]},
        ]}]);

        const links = [...container.querySelectorAll("li a")];
        expect(links.map(link => link.getAttribute("href"))).toEqual([
            "/test/word/q/a%2Fb%3F%23/10", "/test/word/q/a%2Fb%3F%23/20"
        ]);
        expect(links.map(link => link.textContent)).toEqual(["a/b?#", "a/b?#"]);
        expect([...container.querySelectorAll("li")].map(issue => issue.textContent)).toEqual([
            "Missing POS for a/b?# 'first word'", "Missing POS for a/b?# 'second word'"
        ]);
    });

    it("links corpus text and rule issues while preserving their groups", () => {
        const container = renderReport([
            {type: "Corpus texts", issues: [
                {fragments: [{...plainText, text: "Mismatched corpus word", linkType: "corpusText", linkId: 30}]}
            ]},
            {type: "Rules", issues: [
                {fragments: [{...plainText, text: "Unknown phoneme", linkType: "rule", linkId: 40}]}
            ]},
        ]);

        expect([...container.querySelectorAll("li a")].map(link => link.getAttribute("href")))
            .toEqual(["/test/corpus/text/30", "/test/rule/40"]);
        expect([...container.querySelectorAll("section h3")].map(heading => heading.textContent))
            .toEqual(["Corpus texts (1)", "Rules (1)"]);
    });
});
