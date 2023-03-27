import {useLoaderData, useParams} from "react-router";
import {Link} from "react-router-dom";
import {useEffect} from "react";

export async function loader({params}) {
    return fetch(process.env.REACT_APP_BACKEND_URL + "paradigms/" + params.lang, { headers: { 'Accept': 'application/json'} })
}

export default function ParadigmList() {
    const paradigmList = useLoaderData()
    const params = useParams()

    useEffect(() => { document.title = `Etymograph : ${paradigmList.langFullName} : Paradigms` })

    return <>
        <h2>
            <small>
                <Link to={`/`}>Etymograph</Link> >{' '}
                {<Link to={`/language/${params.lang}`}>{paradigmList.langFullName}</Link>} >{' '}
            </small>
            Paradigms
        </h2>
        <ul>
            {paradigmList.paradigms.map(p => <li key={p.id}><Link to={`/paradigm/${p.id}`}>{p.name}</Link></li>)}
        </ul>
        <Link to={`/paradigms/${params.lang}/new`}>Add paradigm</Link>
    </>
}
