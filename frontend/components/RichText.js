import Link from "next/link";
import {useRouter} from "next/router";

function RichTextFragment(params) {
    const router = useRouter()
    if (params.fragment.linkType === "rule") {
        return <Link href={`/${router.query.graph}/rule/${params.fragment.linkId}`}>{params.fragment.text}</Link>
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
