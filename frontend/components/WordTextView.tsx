import {Syllabogram, SyllabogramSequence} from "@/models";

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

export default function WordTextView(params: { syllabograms?: SyllabogramSequence, text: string }) {
    if (!params.syllabograms) {
        return <>{params.text}</>
    }
    return <>{params.syllabograms.syllabograms.map((s, index) =>
        <SyllabogramView syllabogram={s} prevSyllabogram={index > 0 ? params.syllabograms.syllabograms[index - 1] : undefined}/>)}
    </>
}
