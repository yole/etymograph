import {useLoaderData, useNavigate, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useEffect, useState} from "react";
import WordForm from "./WordForm";
import {deleteLink, deleteWord, updateLink} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.lang}/${params["*"]}`, { headers: { 'Accept': 'application/json'} })
}

function WordLinkComponent(params) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord
    const [editMode, setEditMode] = useState(false)
    const [ruleNames, setRuleNames] = useState(linkWord.ruleNames.join(","))
    const [errorText, setErrorText] = useState("")

    function deleteLinkClicked() {
        if (window.confirm("Delete this link?")) {
            deleteLink(baseWord.id, linkWord.id, params.linkType.typeId)
                .then(() => params.revalidator.revalidate())
        }
    }

    function saveLink() {
        updateLink(baseWord.id, linkWord.id, params.linkType.typeId, ruleNames)
            .then((response) => {
                if (response.status === 200) {
                    setErrorText("")
                    setEditMode(false)
                    params.revalidator.revalidate()
                }
                else {
                    response.json().then(r => setErrorText(r.message))
                }
            })
    }

    return <div>
        {linkWord.language !== baseWord.language && linkWord.language + " "}
        <Link to={`/word/${linkWord.language}/${linkWord.text}`}>{linkWord.text}</Link>
        {linkWord.gloss != null && ' "' + linkWord.gloss + '"' }
        {linkWord.ruleIds.length > 0 && <>&nbsp;(
            {linkWord.ruleIds.map((ruleId, index) => <>
              {index > 0 && ", "}
              <Link to={`/rule/${ruleId}`}>{linkWord.ruleNames[index]}</Link>
            </>)}
        )</>}
        &nbsp;<span className="inlineButtonLink">
                    (<button className="inlineButton" onClick={() => setEditMode(!editMode)}>edit</button>
                </span>
        &nbsp;|&nbsp;<span className="inlineButtonLink">
                    <button className="inlineButton" onClick={() => deleteLinkClicked()}>delete</button>)
                </span>
        {editMode && <>
            <table><tbody>
                <tr>
                    <td>Rule names:</td>
                    <td><input type="text" value={ruleNames} onChange={e => setRuleNames(e.target.value)}/></td>
                </tr>
                </tbody></table>
            <button onClick={() => saveLink()}>Save</button>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </>}
    </div>
}

function WordLinkTypeComponent(params) {
    return params.links.map(l => <>
        <div>{l.type}</div>
        {l.words.map(w => <WordLinkComponent baseWord={params.word} linkWord={w} linkType={l} revalidator={params.revalidator}/>)}
        <p/>
    </>)
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
    useEffect(() => { document.title = "Etymograph : " + word.text })

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

    return <>
        <h2><small>
            <Link to={`/language/${word.language}`}>{word.languageFullName}</Link> >
            <Link to={`/dictionary/${word.language}`}>Dictionary</Link> > </small>
            {word.text}</h2>
        {!editMode && <>
            {word.pos && <div>{word.pos}</div>}
            <p>{word.gloss}</p>
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

        <WordLinkTypeComponent word={word} links={word.linksFrom} revalidator={revalidator}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} revalidator={revalidator}/>

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
        {!word.glossComputed && <Link to={`/word/${word.language}/${word.id}/paradigms`}>Paradigms</Link>}
    </>
}

export function WordError() {
    return <div>No such word in the dictionary</div>
}
