import Link from "next/link";

function RichTextFragment(params) {
    if (params.fragment.linkType === "rule") {
        return <Link href={`/rule/${params.fragment.linkId}`}>{params.fragment.text}</Link>
    }
    if (params.fragment.emph) {
        return <span className="richTextEmph">{params.fragment.text}</span>
    }
    return params.fragment.text
}

export default function RichText(params) {
    return params.richText.fragments.map(fragment => <RichTextFragment fragment={fragment}/>)
}
