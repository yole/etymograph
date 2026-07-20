import {describe, expect, it} from "vitest";
import {CorpusLineViewModel, CorpusTextViewModel, CorpusWordViewModel, TranslationViewModel} from "@/models";
import {buildSegments} from "./[id]";

function word(index: number): CorpusWordViewModel {
    return {
        index,
        text: `word-${index}`,
        normalizedText: `word-${index}`,
        gloss: "",
        glossable: true,
        homonym: false,
    };
}

function line(...indices: number[]): CorpusLineViewModel {
    return {
        words: indices.map(word),
        separator: false,
    };
}

function translation(id: number, anchorStartIndex?: number | null, anchorEndIndex?: number | null): TranslationViewModel {
    return {
        id,
        text: `translation-${id}`,
        source: [],
        sourceEditableText: "",
        anchorStartIndex,
        anchorEndIndex,
    };
}

function corpusText(lines: CorpusLineViewModel[], translations: TranslationViewModel[]): CorpusTextViewModel {
    return {
        id: 1,
        title: "Test text",
        language: "q",
        languageFullName: "Quenya",
        syllabographic: false,
        text: "",
        lines,
        source: [],
        sourceEditableText: "",
        translations,
    };
}

describe("buildSegments", () => {
    it("splits the text at translation anchors and groups translations with identical anchors", () => {
        const first = translation(1, 0, 2);
        const second = translation(2, 2, 3);
        const sameSegment = translation(3, 2, 3);

        const result = buildSegments(corpusText([line(0, 1), line(2, 3)], [first, second, sameSegment]));

        expect(result.segments).toEqual([
            {start: 0, end: 2, translations: [first]},
            {start: 2, end: 3, translations: [second, sameSegment]},
            {start: 3, end: 4, translations: []},
        ]);
        expect(result.unanchoredTranslations).toEqual([]);
    });

    it("keeps missing and invalid anchors out of the segments", () => {
        const missingStart = translation(1, null, 1);
        const missingEnd = translation(2, 0, undefined);
        const negativeStart = translation(3, -1, 1);
        const pastEnd = translation(4, 0, 3);
        const emptyRange = translation(5, 1, 1);
        const reversedRange = translation(6, 2, 1);
        const invalid = [missingStart, missingEnd, negativeStart, pastEnd, emptyRange, reversedRange];

        const result = buildSegments(corpusText([line(0, 1)], invalid));

        expect(result.segments).toEqual([{start: 0, end: 2, translations: []}]);
        expect(result.unanchoredTranslations).toEqual(invalid);
    });

    it("returns no segments for an empty text", () => {
        const unanchored = translation(1, 0, 1);

        expect(buildSegments(corpusText([], [unanchored]))).toEqual({
            segments: [],
            unanchoredTranslations: [unanchored],
        });
    });
});
