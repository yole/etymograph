import Link from "next/link";

export default function WordLink(params) {
    const word = params.word
    let linkTarget = `/word/${word.language}/${word.text.toLowerCase()}`;
    if (word.homonym) {
        linkTarget += `/${word.id}`
    }
    return <>
        {params.baseLanguage !== undefined && word.language !== params.baseLanguage && word.displayLanguage + " "}
        <Link href={linkTarget}>{word.reconstructed && "*"}{word.text}</Link>
    </>
}

