import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch(`http://localhost:8080/dictionary/${params.lang}`, { headers: { 'Accept': 'application/json'} })
}

export default function Dictionary() {
    const dict = useLoaderData()
    return <>
        <h2>Dictionary for {dict.language.name}</h2>
        <ul>
            {dict.words.map(w => <li><Link to={`/word/${dict.language.shortName}/${w.text}`}>{w.text}</Link> - {w.gloss}</li>)}
        </ul>
    </>
}
