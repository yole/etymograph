import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import {CorpusLangViewModel} from "@/models";
import LanguageNavBar from "@/components/LanguageNavBar";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `corpus/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function CorpusLangIndex(props) {
    const corpusForLanguage = props.loaderData as CorpusLangViewModel
    const router = useRouter()
    const graph = router.query.graph
    return <>
        <Breadcrumbs langId={corpusForLanguage.language} langName={corpusForLanguage.languageFullName} title="Corpus"/>
        <LanguageNavBar langId={corpusForLanguage.language}/>
        <p/>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                    <li key={t.id}><Link href={`/${graph}/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
        {allowEdit() && <button className="uiButton" onClick={() => router.push(`/${graph}/corpus/${corpusForLanguage.language}/new`)}>Add</button>}
    </>
}
