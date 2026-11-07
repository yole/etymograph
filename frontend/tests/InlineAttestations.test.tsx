// @vitest-environment jsdom
import {renderToStaticMarkup} from "react-dom/server";
import {expect, it} from "vitest";
import InlineAttestations from "@/components/InlineAttestations";

it("shows every explicit line beside its text title and preserves the attested form", () => {
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<InlineAttestations graph="test" attestations={[
        {textId: 1, textTitle: "Sample text", lineNumbers: ["1", "2a"], word: "elena"},
        {textId: 2, textTitle: "Other text", lineNumbers: ["7"]}
    ]}/>);

    expect(container.textContent).toBe("  in Sample text 1, 2a (elena), Other text 7");
    expect(container.querySelector('a[href="/test/corpus/text/1"]')?.textContent).toBe("Sample text");
    expect(container.querySelector('a[href="/test/corpus/text/2"]')?.textContent).toBe("Other text");
});

it("shows only the text title when no explicit lines exist", () => {
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<InlineAttestations graph="test" attestations={[
        {textId: 1, textTitle: "Unnumbered text", lineNumbers: []}
    ]}/>);

    expect(container.textContent).toBe("  in Unnumbered text");
});
