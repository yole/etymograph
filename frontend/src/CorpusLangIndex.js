import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch("http://localhost:8080/corpus/" + params.langId, { headers: { 'Accept': 'application/json'} })
}

export default function CorpusLangIndex() {
    const corpusForLanguage = useLoaderData()
    return <>
        <p>Corpus for {corpusForLanguage.language.name}</p>
        <ul>
            {corpusForLanguage.corpusTexts.map(t => (
                <li key={t.id}><Link to={`/corpus/text/${t.id}`}>{t.title}</Link></li>
                )
            )}
        </ul>
    </>
}
