import {useLoaderData, useParams} from "react-router";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch(process.env.REACT_APP_BACKEND_URL + "paradigms/" + params.lang, { headers: { 'Accept': 'application/json'} })
}

export default function ParadigmList() {
    const paradigms = useLoaderData()
    const params = useParams()
    return <>
        <ul>
            {paradigms.map(p => <li key={p.id}><Link to={`/paradigm/${p.id}`}>{p.name}</Link></li>)}
        </ul>
        <Link to={`/paradigms/${params.lang}/new`}>Add paradigm</Link>
    </>
}
