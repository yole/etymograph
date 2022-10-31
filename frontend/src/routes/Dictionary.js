import {useLoaderData, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import WordForm from "./WordForm";

export async function loader({params}) {
    return fetch(`http://localhost:8080/dictionary/${params.lang}`, { headers: { 'Accept': 'application/json'} })
}

export default function Dictionary() {
    const dict = useLoaderData()
    const revalidator = useRevalidator()

    return <>
        <h2>Dictionary for {dict.language.name}</h2>
        <h3>Add word</h3>
        <WordForm language={dict.language} submitted={() => revalidator.revalidate()}/>
        <ul>
            {dict.words.map(w => <li><Link to={`/word/${dict.language.shortName}/${w.text}`}>{w.text}</Link> - {w.gloss}</li>)}
        </ul>
    </>
}
