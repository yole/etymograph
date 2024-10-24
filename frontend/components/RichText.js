import Link from "next/link";
import {useRouter} from "next/router";

function RichTextFragment(params) {
    const router = useRouter()
    const graph = router.query.graph
    if (params.fragment.linkType === "rule") {
        return <Link href={`/${graph}/rule/${params.fragment.linkId}`}>{params.fragment.text}</Link>
    }
    if (params.fragment.linkType === "word") {
        return <Link href={`/${graph}/word/${params.fragment.linkLanguage}/${params.fragment.linkData}/${params.fragment.linkId}`}>
            {params.fragment.text}
        </Link>
    }

    const textWithTooltip = (params.fragment.tooltip !== null)
        ? <span className="richTextTooltip" title={params.fragment.tooltip}>{params.fragment.text}</span>
        : params.fragment.text

    if (params.fragment.emph) {
        return <span className="richTextEmph">{textWithTooltip}</span>
    }
    return textWithTooltip
}

export default function RichText(params) {
    return params.richText.fragments.map(fragment => <RichTextFragment fragment={fragment}/>)
}
