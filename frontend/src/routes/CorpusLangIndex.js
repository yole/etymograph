import {useLoaderData, useNavigate} from "react-router";
import {Link} from "react-router-dom";
import {useEffect} from "react";

export async function loader({params}) {
    return fetch("http://localhost:8080/corpus/" + params.langId, { headers: { 'Accept': 'application/json'} })
}

export default function CorpusLangIndex() {
    const corpusForLanguage = useLoaderData()
    const navigate = useNavigate()
    useEffect(() => { document.title = "Etymograph : " + corpusForLanguage.language.name + " : Corpus"})
    return <>
        <h2><small><Link to={`/`}>Etymograph</Link> > <Link to={`/language/${corpusForLanguage.language.shortName}`}>{corpusForLanguage.language.name}</Link></small> > Corpus</h2>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                <li key={t.id}><Link to={`/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
        <button onClick={() => navigate(`/corpus/${corpusForLanguage.language.shortName}/new`)}>Add</button>
    </>
}
