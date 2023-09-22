import {allowEdit, fetchBackend} from "@/api";
import {useEffect, useState} from "react";
import Link from "next/link";
import PublicationForm from "@/components/PublicationForm";
import {useRouter} from "next/router";

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

    useEffect(() => { document.title = "Etymograph : Bibliography : " + publication.refId })

    function submitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/publications`}>Bibliography</Link> {'> '}
            </small>
            {publication.refId}
        </h2>

        {!editMode && <>
            <p>{publication.name}</p>
        </>}
        {editMode && <PublicationForm
            updateId={publication.id}
            initialName={publication.name}
            initialRefId={publication.refId}
            submitted={submitted}
        />}

        {allowEdit() && <>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        </>}
    </>
}
