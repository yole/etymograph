import {allowEdit, fetchAllGraphs, fetchBackend} from "@/api";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useRouter} from "next/router";
import {PublicationViewModel} from "@/models";

export const getStaticPaths = fetchAllGraphs

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `publications`)
}

export default function Publications(props) {
    const publications = props.loaderData as PublicationViewModel[];
    const router = useRouter()
    const graph = router.query.graph

    return <>
        <Breadcrumbs title="Bibliography"/>

        {publications.map(p => <>
            <Link href={`/${graph}/publication/${p.id}`}>{p.refId}</Link>: {p.name}
            <br/>
        </>)}

        <p>{allowEdit() && <button onClick={() => router.push(`/${graph}/publications/new`)}>Add publication</button>}</p>
    </>
}
