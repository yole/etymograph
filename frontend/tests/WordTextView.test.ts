import {describe, expect, it} from "vitest";
import {wordTextToPlainText} from "@/components/WordTextView";

describe("wordTextToPlainText", () => {
    it("keeps ordinary word text unchanged", () => {
        expect(wordTextToPlainText("šum")).toBe("šum");
    });

    it("uses parsed syllabograms instead of raw markup", () => {
        expect(wordTextToPlainText("^d^_IŠKUR", {syllabograms: [
            {type: "Determinative", text: "d"},
            {type: "LogogramAlt", text: "IŠKUR"},
        ]})).toBe("dIŠKUR");
    });

    it("preserves syllable separators, logogram separators, and sign numbers", () => {
        expect(wordTextToPlainText("raw markup", {syllabograms: [
            {type: "Syllabogram", text: "a"},
            {type: "Syllabogram", text: "na"},
            {type: "Logogram", text: "DU3"},
            {type: "Logogram", text: "A"},
            {type: "LogogramAlt", text: "ŠU"},
        ]})).toBe("a-na.DU3.A-ŠU");
    });

    it("renders a trailing alternate determinative without markup or a separator", () => {
        expect(wordTextToPlainText("raw markup", {syllabograms: [
            {type: "Logogram", text: "GIŠ"},
            {type: "DeterminativeAlt", text: "ki"},
        ]})).toBe("GIŠki");
    });
});
