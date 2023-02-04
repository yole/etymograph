import {useLoaderData, useParams} from "react-router";
import {useEffect} from "react";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch( `${process.env.REACT_APP_BACKEND_URL}language/${params.langId}`, { headers: { 'Accept': 'application/json'} })
}

export default function LanguageIndex() {
    const lang = useLoaderData()
    const params = useParams()
    useEffect(() => { document.title = "Etymograph : " + lang.name })

    return <>
        <h2>{lang.name}</h2>
        <h3>Phonetics</h3>
        <ul>
        {lang.phonemeClasses.map(pc => <li>{pc.name}: {pc.matchingPhonemes.join(", ")}</li>)}
        </ul>
        <Link to={`/dictionary/${params.langId}`}>Dictionary</Link><br/>
        <Link to={`/dictionary/${params.langId}/compounds`}>Compound Words</Link><br/>
        <Link to={`/paradigms/${params.langId}`}>Paradigms</Link><br/>
        <Link to={`/corpus/${params.langId}`}>Corpus</Link>
    </>
}
