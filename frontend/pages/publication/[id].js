import {allowEdit, fetchBackend} from "@/api";
import {useState} from "react";
import PublicationForm from "@/forms/PublicationForm";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`publication/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`publications`)
    const paths = props.loaderData.map(p => ({params: {id: p.id.toString()}}))
    return {paths, fallback: allowEdit()}
}

export default function Publication(props) {
    const publication = props.loaderData
    const [editMode, setEditMode] = useState(false)
    const router = useRouter()

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    return <>
        <Breadcrumbs steps={[{title: "Bibliography", url: `/publications`}]} title={publication.refId} />

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
