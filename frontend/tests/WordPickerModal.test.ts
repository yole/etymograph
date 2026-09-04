import {expect, it} from "vitest";
import {normalizeSearchText} from "@/components/WordPickerModal";

it("matches diacritic-bearing words with plain search letters", () => {
    expect(normalizeSearchText("šum")).toBe("sum");
    expect(normalizeSearchText("s")).toBe("s");
});
