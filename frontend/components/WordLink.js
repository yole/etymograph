import Link from "next/link";
import {useRouter} from "next/router";

export default function WordLink(params) {
    const router = useRouter()
    const graph = router.query.graph

    const word = params.word
    let linkTarget = `/${graph}/word/${word.language}/${word.text.toLowerCase()}`;
    if (word.homonym) {
        linkTarget += `/${word.id}`
    }
    return <>
        {params.baseLanguage !== undefined && word.language !== params.baseLanguage && word.displayLanguage + " "}
        <Link href={linkTarget}>{word.reconstructed && "*"}{word.text}</Link>
    </>
}

