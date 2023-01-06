import {useLoaderData, useNavigate, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useState} from "react";
import WordForm from "./WordForm";
import {deleteLink, deleteWord} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.lang}/${params["*"]}`, { headers: { 'Accept': 'application/json'} })
}

export default function Word() {
    const word = useLoaderData()
    const revalidator = useRevalidator()
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [editMode, setEditMode] = useState(false)
    const navigate = useNavigate()

    function submitted() {
        setShowBaseWord(false)
        setShowDerivedWord(false)
        setShowCompoundComponent(false)
        setShowRelated(false)
        revalidator.revalidate()
    }

    function editSubmitted() {
        setEditMode(false)
        revalidator.revalidate()
    }

    function deleteWordClicked() {
        if (window.confirm("Delete this word?")) {
            deleteWord(word.id)
                .then(() => navigate("/dictionary/" +  word.language))
        }
    }

    function deleteLinkClicked(fromWord, toWord, linkType) {
        if (window.confirm("Delete this link?")) {
            deleteLink(fromWord, toWord, linkType)
                .then(() => revalidator.revalidate())
        }
    }

    return <>
        <h2>{word.text}</h2>
        {!editMode && <>
            {word.pos && <div>{word.pos}</div>}
            <p><Link to={`/dictionary/${word.language}`}>{word.language}</Link> {word.gloss}</p>
            {word.notes && <p>{word.notes}</p>}
            {word.source != null && <div className="source">Source: {word.source}</div>}
        </>}
        {editMode && <WordForm language={word.language} updateId={word.id}
                               initialGloss={word.glossComputed ? undefined : word.gloss}
                               initialPos={word.pos}
                               initialSource={word.source}
                               initialNotes={word.notes}
                               submitted={editSubmitted}/>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        {!editMode && <button onClick={() => deleteWordClicked()}>Delete</button>}
        {word.linksFrom.map(l => <>
            <div>{l.type}</div>
            {l.words.map(w => <div>
                {w.language !== word.language && w.language + " "}
                <Link to={`/word/${w.language}/${w.text}`}>{w.text}</Link>
                {w.ruleIds.length > 0 && <>&nbsp;(<Link to={`/rule/${w.ruleIds[0]}`}>rule</Link>)</>}
                &nbsp;<span className="deleteLink">
                    (<button className="deleteLinkButton" onClick={() => deleteLinkClicked(word.id, w.id, l.typeId)}>x</button>)
                </span>
            </div>)}
        </>)}
        {word.linksTo.map(l => <>
            <div>{l.type}</div>
            {l.words.map(w => <div>
                {w.language !== word.language && w.language + " "}
                <Link to={`/word/${w.language}/${w.text}`}>{w.text}</Link>
                {w.ruleIds.length > 0 && <>&nbsp;(<Link to={`/rule/${w.ruleIds[0]}`}>rule</Link>)</>}
                &nbsp;<span className="deleteLink">
                    (<button className="deleteLinkButton" onClick={() => deleteLinkClicked(w.id, word.id, l.typeId)}>x</button>)
                </span>
            </div>)}
        </>)}

        <p/>
        <a href="#" onClick={() => setShowBaseWord(!showBaseWord)}>Add base word</a><br/>
        {showBaseWord && <WordForm submitted={submitted} derivedWord={word}/>}
        <a href="#" onClick={() => setShowDerivedWord(!showDerivedWord)}>Add derived word</a><br/>
        {showDerivedWord && <WordForm submitted={submitted} baseWord={word}/>}
        <a href="#" onClick={() => setShowCompoundComponent(!showCompoundComponent)}>Add component of compound</a><br/>
        {showCompoundComponent && <WordForm submitted={submitted} compoundWord={word} language={word.language}/>}
        <a href="#" onClick={() => setShowRelated(!showRelated)}>Add related word</a><br/>
        {showRelated && <WordForm submitted={submitted} relatedWord={word} language={word.language}/>}
        <p/>
        <Link to={`/word/${word.language}/${word.id}/paradigms`}>Paradigms</Link>
    </>
}

export function WordError() {
    return <div>No such word in the dictionary</div>
}
