import {fetchAllLanguagePaths, fetchAllLanguagePathsEditable, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import ParadigmForm from "@/forms/ParadigmForm"
import {useRouter} from "next/router";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePathsEditable

export default function ParadigmEditor() {
    const router = useRouter()
    const graph = router.query.graph
    const lang = router.query.lang as string;
    return <>
        <Breadcrumbs langId={lang} title="New Paradigm"/>
        <ParadigmForm lang={lang} redirectOnCreate={(r) => `/${graph}/paradigm/${r.id}`}/>
    </>
}
