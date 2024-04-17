import {allowEdit, fetchAllGraphs, fetchBackend} from "@/api";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";

export const getStaticPaths = fetchAllGraphs

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph,`publications`)
}

export default function Publications(props) {
    const publications = props.loaderData
    const router = useRouter()
    const graph = router.query.graph

    return <>
        <Breadcrumbs title="Bibliography"/>

        {publications.map(p => <>
            <Link href={`/${graph}/publication/${p.id}`}>{p.refId}</Link>: {p.name}
            <br/>
        </>)}

        <p>{allowEdit() && <Link href={`/${graph}/publications/new`}>Add publication</Link>}</p>
    </>
}
