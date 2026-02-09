import {Syllabogram, SyllabogramSequence} from "@/models";
import WordWithStress from "@/components/WordWithStress";

function SyllabogramView(params: {syllabogram: Syllabogram, prevSyllabogram?: Syllabogram}) {
    let syl = params.syllabogram
    const delimiter = !params.prevSyllabogram || syl.type == "Determinative" || params.prevSyllabogram.type == "Determinative"
        ? ""
        : (syl.type == "Logogram" ? "." : "-")

    const renderTextWithSubscript = (text: string) => {
        const match = text.match(/^(\D+)(\d+)$/)
        if (match && match[1]) {
            return <>{match[1]}<sub>{match[2]}</sub></>
        }
        return <>{text}</>
    }

    return <>
        {delimiter}
        {syl.type == "LogogramAlt" && <i>{syl.text}</i>}
        {syl.type == "Determinative" && <sup>{syl.text}</sup>}
        {syl.type != "LogogramAlt" && syl.type != "Determinative" && <>{renderTextWithSubscript(syl.text)}</>}
    </>
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
        <SyllabogramView syllabogram={s} prevSyllabogram={index > 0 ? params.syllabograms.syllabograms[index - 1] : undefined}/>)}
    </>
}
