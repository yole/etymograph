import Link from "next/link";
import {useRouter} from "next/router";
import WordGloss from "@/components/WordGloss";
import {WordRefViewModel} from "@/models";
import WordTextView from "@/components/WordTextView";
import {Urls} from "@/components/Urls";

interface WordLinkProps {
    word: WordRefViewModel;
    baseLanguage?: string;
    gloss?: boolean;
}

export default function WordLink(params: WordLinkProps) {
    const router = useRouter()
    const graph = router.query.graph as string

    const word = params.word
    let linkTarget = Urls.Words.fromRef(graph, word)
    const textWithSegments = word.segmentedText ?? word.text
    const wordText = word.reconstructed ? ("*" + textWithSegments) : textWithSegments
    return <>
        {params.baseLanguage !== undefined && word.language !== params.baseLanguage && word.displayLanguage + " "}
        <Link href={linkTarget}>
            <WordTextView text={wordText} syllabograms={word.syllabogramSequence} />
        </Link>
        {params.gloss && word.gloss !== null && <>{' '}&apos;<WordGloss gloss={word.gloss}/>&apos;</>}
    </>
}
