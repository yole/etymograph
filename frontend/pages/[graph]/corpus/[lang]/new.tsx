import CorpusTextForm from "@/forms/CorpusTextForm";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function CorpusTextEditor(params) {
    const langData = params.loaderData
    const router = useRouter()

    const lang = router.query.lang as string;

    return <>
        <Breadcrumbs langName={langData.name} langId={lang} title="New Corpus Text"/>
        <CorpusTextForm lang={lang} redirectOnCreate={r => `/${router.query.graph}/corpus/text/${r.id}`}/>
    </>
}
