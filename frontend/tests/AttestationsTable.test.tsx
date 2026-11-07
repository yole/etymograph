// @vitest-environment jsdom
import {renderToStaticMarkup} from "react-dom/server";
import {expect, it} from "vitest";
import AttestationsTable from "@/components/AttestationsTable";

it("groups exact attested forms with their corpus text locations", () => {
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<AttestationsTable graph="test" word={{
        text: "elen", languageSyllabographic: false, attestations: [
            {textId: 1, textTitle: "First text", word: "Elen", lineNumbers: ["1"]},
            {textId: 1, textTitle: "First text", word: "elen", lineNumbers: ["2"]},
            {textId: 2, textTitle: "Second text", word: "Elen", lineNumbers: []},
            {textId: 1, textTitle: "First text", word: "Elen", lineNumbers: ["1", "3"]},
            {textId: 3, textTitle: "Third text", word: null, lineNumbers: ["4"]}
        ]
    }}/>);
    expect([...container.querySelectorAll("thead th")].map(th => th.textContent))
        .toEqual(["Attested form", "Locations"]);
    const rows = [...container.querySelectorAll("tbody tr")];
    expect(rows.map(row => [...row.children].map(cell => cell.textContent)))
        .toEqual([["Elen", "First text 1, 3, Second text"], ["elen", "First text 2, Third text 4"]]);
    expect(rows[0].querySelector('a[href="/test/corpus/text/1"]')?.textContent).toBe("First text");
    expect(container.querySelectorAll('a[href="/test/corpus/text/1"]')).toHaveLength(2);
});

it("renders exact syllabographic transliterations separately from location lists", () => {
    const container = document.createElement("div");
    container.innerHTML = renderToStaticMarkup(<AttestationsTable graph="test" word={{
        text: "tarkun", languageSyllabographic: true, attestations: [
            {textId: 1, textTitle: "First text", word: "^d^_DU3", lineNumbers: ["1"],
                syllabogramSequence: {syllabograms: [
                    {type: "Determinative", text: "d"}, {type: "LogogramAlt", text: "DU3"}
                ]}},
            {textId: 1, textTitle: "First text", word: "tar-ku-un", lineNumbers: ["2"]}
        ]
    }}/>);
    expect(container.querySelector("thead th")?.textContent).toBe("Transliteration");
    const rows = [...container.querySelectorAll("tbody tr")];
    expect(rows.map(row => row.lastElementChild?.textContent)).toEqual(["First text 1", "First text 2"]);
    expect(rows[0].querySelector("sup")?.textContent).toBe("d");
    expect(rows[0].querySelector("i")?.textContent).toBe("DU3");
    expect(rows[0].querySelector("sub")?.textContent).toBe("3");
    expect(rows[1].firstElementChild?.textContent).toBe("tar-ku-un");
});
