import {useLoaderData, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useState} from "react";
import WordForm from "./WordForm";


export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.lang}/${params.text}`, { headers: { 'Accept': 'application/json'} })
}

export default function Word() {
    const word = useLoaderData()
    const revalidator = useRevalidator()
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)

    function submitted() {
        setShowBaseWord(false)
        setShowDerivedWord(false)
        setShowCompoundComponent(false)
        revalidator.revalidate()
    }

    return <>
        <h2>{word.text}</h2>
        <p>{word.gloss}</p>
        {word.linksFrom.map(l => <>
            <div>{l.type}</div>
            {l.words.map(w => <div>
                <Link to={`/word/${w.language}/${w.text}`}>{w.text}</Link>
                {w.ruleId !== null && <>&nbsp;(<Link to={`/rule/${w.ruleId}`}>rule</Link>)</>}
            </div>)}
        </>)}
        {word.linksTo.map(l => <>
            <div>{l.type}</div>
            {l.words.map(w => <div>
                <Link to={`/word/${w.language}/${w.text}`}>{w.text}</Link>
                {w.ruleId !== null && <>&nbsp;(<Link to={`/rule/${w.ruleId}`}>rule</Link>)</>}
            </div>)}
        </>)}

        <p/>
        <a href="#" onClick={() => setShowBaseWord(!showBaseWord)}>Add base word</a><br/>
        {showBaseWord && <WordForm submitted={submitted} derivedWord={word}/>}
        <a href="#" onClick={() => setShowDerivedWord(!showDerivedWord)}>Add derived word</a><br/>
        {showDerivedWord && <WordForm submitted={submitted} baseWord={word}/>}
        <a href="#" onClick={() => setShowCompoundComponent(!showCompoundComponent)}>Add component of compound</a><br/>
        {showCompoundComponent && <WordForm submitted={submitted} compoundWord={word}/>}
    </>
}
