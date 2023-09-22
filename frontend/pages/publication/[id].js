import {allowEdit, fetchBackend} from "@/api";
import {useEffect} from "react";
import Link from "next/link";

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
    useEffect(() => { document.title = "Etymograph : Bibliography : " + publication.refId })

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/publications`}>Bibliography</Link> {'> '}
            </small>
            {publication.refId}
        </h2>

        <p>{publication.name}</p>
    </>
}
