import {fetchAllLanguagePathsEditable, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import ParadigmForm from "@/forms/ParadigmForm"
import {useRouter} from "next/router";
import {Urls} from "@/components/Urls";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePathsEditable

export default function ParadigmEditor() {
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = router.query.lang as string;
    return <>
        <Breadcrumbs langId={lang} title="New Paradigm"/>
        <ParadigmForm lang={lang} redirectOnCreate={(r) => Urls.Rules.morpho(graph, r.language, r.id)}/>
    </>
}
