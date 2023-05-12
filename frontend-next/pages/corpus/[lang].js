import {useEffect} from "react";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";

export async function getStaticProps(context) {
    return fetchBackend(`corpus/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths()
}

export default function CorpusLangIndex(props) {
    const corpusForLanguage = props.loaderData
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + corpusForLanguage.language.name + " : Corpus"})
    return <>
        <h2><small><Link href={`/`}>Etymograph</Link> {'>'} <Link href={`/language/${corpusForLanguage.language.shortName}`}>{corpusForLanguage.language.name}</Link></small> {'>'} Corpus</h2>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                    <li key={t.id}><Link href={`/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
        {/*<button onClick={() => navigate(`/corpus/${corpusForLanguage.language.shortName}/new`)}>Add</button>*/}
    </>
}
