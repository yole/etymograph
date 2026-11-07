import {describe, expect, it} from "vitest";
import {CorpusLineViewModel, CorpusTextViewModel, CorpusWordViewModel, TranslationViewModel} from "@/models";
import {buildSegments} from "@/pages/[graph]/corpus/text/[id]";

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
        consistencyIssues: [],
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

    it("includes an unglossable gap on the same line in the following chunk", () => {
        const first = translation(1, 0, 1);
        const second = translation(2, 3, 4);
        const words = line(0, 1, 2, 3);
        words.words[1].glossable = false;
        words.words[2].glossable = false;

        const result = buildSegments(corpusText([words], [second, first]));

        expect(result.segments).toEqual([
            {start: 0, end: 1, translations: [first]},
            {start: 1, end: 4, translations: [second]},
        ]);
        expect(second.anchorStartIndex).toBe(3);
    });

    it("attaches both ends of an unglossable gap to the chunks sharing their lines", () => {
        const first = translation(1, 0, 1);
        const second = translation(2, 4, 6);
        const lines = [line(0, 1), line(2, 3, 4), line(5)];
        lines.flatMap(line => line.words).slice(1, 4).forEach(word => word.glossable = false);

        expect(buildSegments(corpusText(lines, [first, second])).segments).toEqual([
            {start: 0, end: 2, translations: [first]},
            {start: 2, end: 6, translations: [second]},
        ]);
        expect(first.anchorEndIndex).toBe(1);
        expect(second.anchorStartIndex).toBe(4);
    });

    it("keeps the gap separate if any gap word is glossable, even on an earlier line", () => {
        const first = translation(1, 0, 1);
        const second = translation(2, 3, 4);
        const lines = [line(0, 1), line(2, 3)];
        lines[1].words[0].glossable = false;

        expect(buildSegments(corpusText(lines, [first, second])).segments).toEqual([
            {start: 0, end: 1, translations: [first]},
            {start: 1, end: 3, translations: []},
            {start: 3, end: 4, translations: [second]},
        ]);
    });

    it("appends unglossable words on the previous chunk's last line to that chunk", () => {
        const first = translation(1, 0, 1);
        const second = translation(2, 3, 4);
        const lines = [line(0, 1, 2), line(3)];
        lines[0].words.slice(1).forEach(word => word.glossable = false);

        expect(buildSegments(corpusText(lines, [first, second])).segments).toEqual([
            {start: 0, end: 3, translations: [first]},
            {start: 3, end: 4, translations: [second]},
        ]);
    });

    it("leaves complete intervening lines separate while attaching both ends of the gap", () => {
        const first = translation(1, 0, 1);
        const sameEnd = translation(2, 0, 1);
        const second = translation(3, 5, 6);
        const lines = [line(0, 1), line(2, 3), line(4, 5)];
        lines.flatMap(line => line.words).slice(1, 5).forEach(word => word.glossable = false);

        expect(buildSegments(corpusText(lines, [first, sameEnd, second])).segments).toEqual([
            {start: 0, end: 2, translations: [first, sameEnd]},
            {start: 2, end: 4, translations: []},
            {start: 4, end: 6, translations: [second]},
        ]);
    });

    it("keeps both ends of the gap separate when any gap word is glossable", () => {
        const first = translation(1, 0, 1);
        const second = translation(2, 4, 5);
        const lines = [line(0, 1), line(2), line(3, 4)];
        lines[0].words[1].glossable = false;
        lines[2].words[0].glossable = false;

        expect(buildSegments(corpusText(lines, [first, second])).segments).toEqual([
            {start: 0, end: 1, translations: [first]},
            {start: 1, end: 4, translations: []},
            {start: 4, end: 5, translations: [second]},
        ]);
    });

    it("keeps leading and trailing unglossable words separate", () => {
        const anchored = translation(1, 1, 2);
        const words = line(0, 1, 2);
        words.words[0].glossable = false;
        words.words[2].glossable = false;

        expect(buildSegments(corpusText([words], [anchored])).segments).toEqual([
            {start: 0, end: 1, translations: []},
            {start: 1, end: 2, translations: [anchored]},
            {start: 2, end: 3, translations: []},
        ]);
    });

    it("shows overlapping ranges once each after their final word", () => {
        const spanning = translation(1, 0, 3);
        const inner = translation(2, 1, 2);
        const overlapping = translation(3, 1, 4);

        const result = buildSegments(corpusText([line(0, 1), line(2, 3)], [spanning, inner, overlapping]));

        expect(result.segments).toEqual([
            {start: 0, end: 1, translations: []},
            {start: 1, end: 2, translations: [inner]},
            {start: 2, end: 3, translations: [spanning]},
            {start: 3, end: 4, translations: [overlapping]},
        ]);
        expect(result.unanchoredTranslations).toEqual([]);
    });

    it("measures gaps from the end of all overlapping translation ranges", () => {
        const spanning = translation(1, 0, 4);
        const inner = translation(2, 1, 2);
        const following = translation(3, 6, 7);
        const words = line(0, 1, 2, 3, 4, 5, 6);
        words.words.slice(2, 6).forEach(word => word.glossable = false);

        expect(buildSegments(corpusText([words], [spanning, inner, following])).segments).toEqual([
            {start: 0, end: 1, translations: []},
            {start: 1, end: 2, translations: [inner]},
            {start: 2, end: 4, translations: [spanning]},
            {start: 4, end: 7, translations: [following]},
        ]);
    });

    it("returns no segments for an empty text", () => {
        const unanchored = translation(1, 0, 1);

        expect(buildSegments(corpusText([], [unanchored]))).toEqual({
            segments: [],
            unanchoredTranslations: [unanchored],
        });
    });
});
