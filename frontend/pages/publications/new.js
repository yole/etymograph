import PublicationForm from "@/forms/PublicationForm";
import Breadcrumbs from "@/components/Breadcrumbs";

export default function PublicationEditor() {
    return <>
        <Breadcrumbs title="New Publication"/>
        <PublicationForm redirectOnCreate={r => `/publication/${r.id}`}/>
    </>
}
