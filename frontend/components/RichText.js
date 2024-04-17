import Link from "next/link";
import {useRouter} from "next/router";

function RichTextFragment(params) {
    const router = useRouter()
    if (params.fragment.linkType === "rule") {
        return <Link href={`/${router.query.graph}/rule/${params.fragment.linkId}`}>{params.fragment.text}</Link>
    }
    if (params.fragment.emph) {
        return <span className="richTextEmph">{params.fragment.text}</span>
    }
    return params.fragment.text
}

export default function RichText(params) {
    return params.richText.fragments.map(fragment => <RichTextFragment fragment={fragment}/>)
}
