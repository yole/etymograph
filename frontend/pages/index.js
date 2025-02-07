import {fetchGraphs} from "../api";
import Link from "next/link";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps() {
    return fetchGraphs()
}

export default function Home(props) {
    const graphs = props.loaderData
    return <>
        <h2>Etymograph</h2>
        <ul>
            {graphs.map(l => <li key={l.id}><Link href={`/${l.id}`}>{l.name}</Link></li>)}
        </ul>
    </>
}
