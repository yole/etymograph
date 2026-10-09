import {Syllabogram, SyllabogramSequence} from "@/models";
import WordWithStress from "@/components/WordWithStress";

function renderTextWithSubscript(text: string) {
    const match = text.match(/^(\D+)(\d+)$/)
    if (match && match[1]) {
        return <>{match[1]}<sub>{match[2]}</sub></>
    }
    return <>{text}</>
}

function syllabogramDelimiter(syl: Syllabogram, index: number, prevSyllabogram?: Syllabogram) {
    return (!prevSyllabogram ||
        syl.type.startsWith("Determinative") ||
        (index == 1 && prevSyllabogram.type.startsWith("Determinative"))
    )
        ? ""
        : (syl.type == "Logogram" ? "." : "-")
}

export function wordTextToPlainText(text: string, syllabograms?: SyllabogramSequence) {
    if (!syllabograms) return text
    return syllabograms.syllabograms.map((syl, index) =>
        syllabogramDelimiter(syl, index, syllabograms.syllabograms[index - 1]) + syl.text
    ).join("")
}

function SyllabogramView(params: {syllabogram: Syllabogram, index: number, prevSyllabogram?: Syllabogram}) {
    const syl = params.syllabogram
    const delimiter = syllabogramDelimiter(syl, params.index, params.prevSyllabogram)

    return <span className="syllabogram">
        {delimiter}
        {syl.type == "LogogramAlt" && <span className="logogram"><i>{renderTextWithSubscript(syl.text)}</i></span>}
        {syl.type == "Determinative" && <sup>{syl.text}</sup>}
        {syl.type == "DeterminativeAlt" && <sup><i>{syl.text}</i></sup>}
        {syl.type == "Logogram" && renderTextWithSubscript(syl.text)}
        {syl.type != "LogogramAlt" && syl.type != "Determinative" && syl.type != "DeterminativeAlt" && syl.type != "Logogram" &&
            <span className="wordText">{renderTextWithSubscript(syl.text)}</span>}
    </span>
}

export default function WordTextView(params: {
    syllabograms?: SyllabogramSequence,
    text: string,
    stressIndex?: number,
    stressLength?: number,
    reconstructed?: boolean
}) {
    if (!params.syllabograms) {
        return <WordWithStress {...params}/>
    }
    return <>{params.syllabograms.syllabograms.map((s, index) =>
        <SyllabogramView syllabogram={s} index={index} prevSyllabogram={index > 0 ? params.syllabograms.syllabograms[index - 1] : undefined}/>)}
    </>
}
