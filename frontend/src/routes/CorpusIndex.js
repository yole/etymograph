import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader() {
    return fetch("http://localhost:8080/corpus", { headers: { 'Accept': 'application/json'} })
}

export default function CorpusIndex() {
    const languages = useLoaderData()
    return <ul>
        {languages.map(l => <li><Link to={`corpus/${l.shortName}`}>{l.name}</Link></li>)}
    </ul>
}
