import CorpusTextForm from "@/forms/CorpusTextForm";
import {fetchAllLanguagePathsEditable, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";
import {Urls} from "@/components/Urls";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePathsEditable

export default function CorpusTextEditor(params) {
    const langData = params.loaderData
    const router = useRouter()

    const lang = router.query.lang as string;

    return <>
        <Breadcrumbs langName={langData.name} langId={lang} title="New Corpus Text"/>
        <CorpusTextForm
            lang={lang}
            focusTarget='title'
            redirectOnCreate={r => Urls.Corpus.text(router.query.graph as string, r.id)}/>
    </>
}
