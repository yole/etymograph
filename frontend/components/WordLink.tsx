import Link from "next/link";
import {useRouter} from "next/router";
import WordGloss from "@/components/WordGloss";
import {WordRefViewModel} from "@/models";

interface WordLinkProps {
    word: WordRefViewModel;
    baseLanguage?: string;
    gloss?: boolean;
}

export default function WordLink(params: WordLinkProps) {
    const router = useRouter()
    const graph = router.query.graph

    const word = params.word
    let linkTarget = `/${graph}/word/${word.language}/${word.text.toLowerCase()}`
    if (word.homonym) {
        linkTarget += `/${word.id}`
    }
    const wordText = word.reconstructed ? ("*" + word.text) : word.text
    return <>
        {params.baseLanguage !== undefined && word.language !== params.baseLanguage && word.displayLanguage + " "}
        <Link href={linkTarget}>{wordText}</Link>
        {params.gloss && word.gloss !== null && <>{' '}&quot;<WordGloss gloss={word.gloss}/>&quot;</>}
    </>
}
