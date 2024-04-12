import CorpusTextForm from "@/forms/CorpusTextForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";

export async function getStaticProps(context) {
    return fetchBackend(`language/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function CorpusTextEditor(params) {
    const langData = params.loaderData
    const router = useRouter()

    const lang = router.query.lang

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${lang}`}>{langData.name}</Link> {'> '}
            </small>
            New Corpus Text
        </h2>

        <CorpusTextForm lang={lang} redirectOnCreate={r => `/corpus/text/${r.id}`}/>
    </>
}
