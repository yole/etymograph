import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";
import {fetchAllGraphs, fetchBackend} from "@/api";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `languages`, true)
}

export const getStaticPaths = fetchAllGraphs

export default function RuleSequenceEditor() {
    const router = useRouter()
    return <>
        <Breadcrumbs title="New Rule Sequence"/>
        <RuleSequenceForm redirectOnCreate={(data) => `/${router.query.graph}/rules/${data.toLang}`}/>
    </>
}
