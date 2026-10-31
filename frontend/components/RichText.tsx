import Link from "next/link";
import {RichTextFragment as RichTextFragmentModel, RichText as RichTextModel} from "@/models";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import WordTextView from "@/components/WordTextView";

function RichTextFragment(params: { fragment: RichTextFragmentModel }) {
    const graph = useContext(GraphContext)
    const text = params.fragment.syllabogramSequence
        ? <WordTextView text={params.fragment.text} syllabograms={params.fragment.syllabogramSequence}/>
        : params.fragment.text
    if (params.fragment.linkType === "rule") {
        return <Link href={`/${graph}/rule/${params.fragment.linkId}`}>{text}</Link>
    }
    if (params.fragment.linkType === "word") {
        return <Link href={`/${graph}/word/${encodeURIComponent(params.fragment.linkLanguage)}/${encodeURIComponent(params.fragment.linkData)}/${params.fragment.linkId}`}>
            {text}
        </Link>
    }
    if (params.fragment.linkType === "phoneme") {
        return <Link href={`/${graph}/phoneme/${params.fragment.linkId}`}>
            {text}
        </Link>
    }
    if (params.fragment.linkType === "corpusText") {
        return <Link href={`/${graph}/corpus/text/${params.fragment.linkId}`}>
            {text}
        </Link>
    }

    const textWithTooltip = params.fragment.tooltip
        ? <span className="richTextTooltip" title={params.fragment.tooltip}>{text}</span>
        : text

    if (params.fragment.emph) {
        return <span className="richTextEmph">{textWithTooltip}</span>
    }
    if (params.fragment.subscript) {
        return <sub>{textWithTooltip}</sub>
    }
    if (params.fragment.monospaced) {
        return <span className="richTextMono">{textWithTooltip}</span>
    }
    return <>{textWithTooltip}</>;
}

export default function RichText(params: { richText: RichTextModel }) {
    return <>{params.richText.fragments.map((fragment, index) => <RichTextFragment key={index} fragment={fragment}/>)}</>
}
