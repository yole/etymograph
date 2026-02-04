import {Syllabogram, SyllabogramSequence} from "@/models";
import WordWithStress from "@/components/WordWithStress";

function SyllabogramView(params: {syllabogram: Syllabogram, prevSyllabogram?: Syllabogram}) {
    let syl = params.syllabogram
    const delimiter = !params.prevSyllabogram || syl.type == "Determinative" || params.prevSyllabogram.type == "Determinative"
        ? ""
        : (syl.type == "Logogram" ? "." : "-")
    return <>
        {delimiter}
        {syl.type == "LogogramAlt" && <i>{syl.text}</i>}
        {syl.type == "Determinative" && <sup>{syl.text}</sup>}
        {syl.type != "LogogramAlt" && syl.type != "Determinative" && <>{syl.text}</>}
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
