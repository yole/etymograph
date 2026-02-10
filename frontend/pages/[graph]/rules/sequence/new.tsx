import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";
import {allowEdit, fetchAllGraphs, fetchAllLanguagePaths, fetchBackend} from "@/api";
import {Urls} from "@/components/Urls";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `languages`, true)
}

export async function getStaticPaths() {
    if (!allowEdit()) return { paths: [], fallback: false }
    return fetchAllGraphs()
}

export default function RuleSequenceEditor() {
    const router = useRouter()
    return <>
        <Breadcrumbs title="New Rule Sequence"/>
        <RuleSequenceForm redirectOnCreate={(data) => Urls.Rules.phono(router.query.graph as string, data.toLang)}/>
    </>
}
