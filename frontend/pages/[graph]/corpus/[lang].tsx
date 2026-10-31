import {fetchAllLanguagePaths, fetchBackend, useAllowEditGraph} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import {CorpusLangViewModel} from "@/models";
import LanguageNavBar from "@/components/LanguageNavBar";
import {useState} from "react";
import ImportTLHDigModal from "@/components/ImportTLHDigModal";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `corpus/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function CorpusLangIndex(props) {
    const corpusForLanguage = props.loaderData as CorpusLangViewModel
    const router = useRouter()
    const graph = router.query.graph
    const [importOpened, setImportOpened] = useState(false)
    return <>
        <Breadcrumbs langId={corpusForLanguage.language} langName={corpusForLanguage.languageFullName} title="Corpus"/>
        <LanguageNavBar langId={corpusForLanguage.language}/>
        <p/>
        {useAllowEditGraph() && <>
            <button type="button" className="uiButton" onClick={() => router.push(`/${graph}/corpus/${corpusForLanguage.language}/new`)}>Add</button>
            {corpusForLanguage.language === "Hitt" && <>{' '}
                <button type="button" className="uiButton" onClick={() => setImportOpened(true)}>Import TLHDig</button>
            </>}
        </>}
        {importOpened && <ImportTLHDigModal onClose={() => setImportOpened(false)}/>}
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                    <li key={t.id}><Link href={`/${graph}/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
    </>
}
