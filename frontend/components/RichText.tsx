import Link from "next/link";
import {RichTextFragment as RichTextFragmentModel, RichText as RichTextModel} from "@/models";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

function RichTextFragment(params: { fragment: RichTextFragmentModel }) {
    const graph = useContext(GraphContext)
    if (params.fragment.linkType === "rule") {
        return <Link href={`/${graph}/rule/${params.fragment.linkId}`}>{params.fragment.text}</Link>
    }
    if (params.fragment.linkType === "word") {
        return <Link href={`/${graph}/word/${params.fragment.linkLanguage}/${params.fragment.linkData}/${params.fragment.linkId}`}>
            {params.fragment.text}
        </Link>
    }
    if (params.fragment.linkType === "phoneme") {
        return <Link href={`/${graph}/phoneme/${params.fragment.linkId}`}>
            {params.fragment.text}
        </Link>
    }

    const textWithTooltip = (params.fragment.tooltip !== null)
        ? <span className="richTextTooltip" title={params.fragment.tooltip}>{params.fragment.text}</span>
        : params.fragment.text

    if (params.fragment.emph) {
        return <span className="richTextEmph">{textWithTooltip}</span>
    }
    return <>{textWithTooltip}</>
}

export default function RichText(params: { richText: RichTextModel }) {
    return <>{params.richText.fragments.map(fragment => <RichTextFragment fragment={fragment}/>)}</>
}
