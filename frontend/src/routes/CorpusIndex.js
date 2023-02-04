import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader() {
    return fetch(process.env.REACT_APP_BACKEND_URL + "corpus", { headers: { 'Accept': 'application/json'} })
}

export default function CorpusIndex() {
    const languages = useLoaderData()
    return <>
        <ul>
        {languages.map(l => <li key={l.shortName}><Link to={`language/${l.shortName}`}>{l.name}</Link></li>)}
        </ul>
        <Link to="/rules">Rules</Link>
    </>
}
