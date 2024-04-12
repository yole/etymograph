import CorpusTextForm from "@/forms/CorpusTextForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

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
        <Breadcrumbs langName={langData.name} langId={lang} title="New Corpus Text"/>
        <CorpusTextForm lang={lang} redirectOnCreate={r => `/corpus/text/${r.id}`}/>
    </>
}
