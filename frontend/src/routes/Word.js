import {useLoaderData} from "react-router";
import {Link} from "react-router-dom";


export async function loader({params}) {
    return fetch(`http://localhost:8080/word/${params.lang}/${params.text}`, { headers: { 'Accept': 'application/json'} })
}

export default function Word() {
    const word = useLoaderData()
    return <>
        <h2>{word.text}</h2>
        <p>{word.gloss}</p>
        {word.linksFrom.map(l => <>
            <div>{l.type}</div>
            {l.words.map(w => <div>
                <Link to={`/word/${word.language}/${w.text}`}>{w.text}</Link>
                {w.ruleId !== undefined && <>&nbsp;(<Link to={`/rule/${word.language}/${w.ruleId}`}>rule</Link>)</>}
            </div>)}
        </>)}
        {word.linksTo.map(l => <>
            <div>Words {l.type} this one</div>
            {l.words.map(w => <div>
                <Link to={`/word/${word.language}/${w.text}`}>{w.text}</Link>
                {w.ruleId !== undefined  && <>&nbsp;(<Link to={`/rule/${word.language}/${w.ruleId}`}>rule</Link>)</>}
            </div>)}
        </>)}
    </>
}
