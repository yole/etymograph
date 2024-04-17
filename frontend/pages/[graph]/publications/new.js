import PublicationForm from "@/forms/PublicationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

export default function PublicationEditor() {
    const graph = useContext(GraphContext)
    return <>
        <Breadcrumbs title="New Publication"/>
        <PublicationForm redirectOnCreate={r => `/${graph}/publication/${r.id}`}/>
    </>
}
