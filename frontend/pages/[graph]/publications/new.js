import PublicationForm from "@/forms/PublicationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";

export default function PublicationEditor() {
    const router = useRouter()
    const graph = router.query.graph
    return <>
        <Breadcrumbs title="New Publication"/>
        <PublicationForm redirectOnCreate={r => `/${graph}/publication/${r.id}`}/>
    </>
}
