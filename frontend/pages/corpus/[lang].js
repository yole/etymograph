import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`corpus/${context.params.lang}`)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function CorpusLangIndex(props) {
    const corpusForLanguage = props.loaderData
    const router = useRouter()
    return <>
        <Breadcrumbs langId={corpusForLanguage.language.shortName} langName={corpusForLanguage.language.name} title="Corpus"/>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                    <li key={t.id}><Link href={`/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
        {allowEdit() && <button onClick={() => router.push(`/corpus/${corpusForLanguage.language.shortName}/new`)}>Add</button>}
    </>
}
