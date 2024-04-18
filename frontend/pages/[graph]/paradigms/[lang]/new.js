import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import ParadigmForm from "@/forms/ParadigmForm"
import {useRouter} from "next/router";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function ParadigmEditor() {
    const router = useRouter()
    const graph = router.query.graph
    return <>
        <Breadcrumbs langId={router.query.lang} title="New Paradigm"/>
        <ParadigmForm lang={router.query.lang} redirectOnCreate={(r) => `/${graph}/paradigm/${r.id}`}/>
    </>
}
