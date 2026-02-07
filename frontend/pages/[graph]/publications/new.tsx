import PublicationForm from "@/forms/PublicationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {fetchAllGraphs, fetchBackend} from "@/api";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `languages`, true)
}

export const getStaticPaths = fetchAllGraphs

export default function PublicationEditor() {
    const graph = useContext(GraphContext)
    return <>
        <Breadcrumbs title="New Publication"/>
        <PublicationForm redirectOnCreate={r => `/${graph}/publication/${r.id}`}/>
    </>
}
