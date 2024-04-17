import {allowEdit, fetchBackend, fetchPathsForAllGraphs} from "@/api";
import {useState} from "react";
import PublicationForm from "@/forms/PublicationForm";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `publication/${context.params.id}`)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("publications", (p) => ({id: p.id.toString()}))
}

export default function Publication(props) {
    const publication = props.loaderData
    const [editMode, setEditMode] = useState(false)
    const router = useRouter()
    const graph = router.query.graph

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    return <>
        <Breadcrumbs steps={[{title: "Bibliography", url: `/${graph}/publications`}]} title={publication.refId} />

        {!editMode && <>
            <p>{publication.name}</p>
        </>}
        {editMode && <PublicationForm
            updateId={publication.id}
            defaultValues={{name: publication.name, refId: publication.refId}}
            submitted={submitted}
        />}

        {allowEdit() && <>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        </>}
    </>
}
