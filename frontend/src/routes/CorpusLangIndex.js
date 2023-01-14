import {useLoaderData, useNavigate} from "react-router";
import {Link} from "react-router-dom";
import {useEffect} from "react";

export async function loader({params}) {
    return fetch("http://localhost:8080/corpus/" + params.langId, { headers: { 'Accept': 'application/json'} })
}

export default function CorpusLangIndex() {
    const corpusForLanguage = useLoaderData()
    const navigate = useNavigate()
    useEffect(() => { document.title = "Etymograph : " + corpusForLanguage.language.name })
    return <>
        <p>Corpus for {corpusForLanguage.language.name}</p>
        <Link to={`/dictionary/${corpusForLanguage.language.shortName}`}>Dictionary</Link><br/>
        <Link to={`/dictionary/${corpusForLanguage.language.shortName}/compounds`}>Compound Words</Link><br/>
        <Link to={`/paradigms/${corpusForLanguage.language.shortName}`}>Paradigms</Link>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                <li key={t.id}><Link to={`/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
        <button onClick={() => navigate(`/corpus/${corpusForLanguage.language.shortName}/new`)}>Add</button>
    </>
}
