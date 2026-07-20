import {Syllabogram, SyllabogramSequence} from "@/models";
import WordWithStress from "@/components/WordWithStress";

function renderTextWithSubscript(text: string) {
    const match = text.match(/^(\D+)(\d+)$/)
    if (match && match[1]) {
        return <>{match[1]}<sub>{match[2]}</sub></>
    }
    return <>{text}</>
}

function SyllabogramView(params: {syllabogram: Syllabogram, index: number, prevSyllabogram?: Syllabogram}) {
    let syl = params.syllabogram
    const delimiter = (!params.prevSyllabogram ||
        syl.type.startsWith("Determinative") ||
        (params.index == 1 && params.prevSyllabogram.type.startsWith("Determinative"))
    )
        ? ""
        : (syl.type == "Logogram" ? "." : "-")

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
