import {useLoaderData, useNavigate, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useEffect, useState} from "react";
import WordForm from "./WordForm";
import {addLink, addWord, deleteLink, deleteWord, updateLink} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}word/${params.lang}/${params["*"]}`, { headers: { 'Accept': 'application/json'} })
}

export function WordLink(params) {
    const word = params.word
    let linkTarget = `/word/${word.language}/${word.text}`;
    if (word.homonym) {
        linkTarget += `/${word.id}`
    }
    return <Link to={linkTarget}>{word.text}</Link>
}

export function WordWithStress(params) {
    const text = params.text
    const stressIndex = params.stressIndex
    const stressLength = params.stressLength
    if (stressIndex != null) {
        return <>
            {text.substring(0, stressIndex)}
            <span className="stressed">{text.substring(stressIndex, stressIndex+stressLength)}</span>
            {text.substring(stressIndex+stressLength)}
        </>
    }
    return text
}

function WordLinkComponent(params) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord
    const [editMode, setEditMode] = useState(false)
    const [ruleNames, setRuleNames] = useState(linkWord.ruleNames.join(","))
    const [errorText, setErrorText] = useState("")

    function deleteLinkClicked() {
        if (window.confirm("Delete this link?")) {
            deleteLink(baseWord.id, linkWord.word.id, params.linkType.typeId)
                .then((r) => {
                    if (r.status === 200) {
                        setErrorText("")
                        params.revalidator.revalidate()
                    }
                    else {
                        r.json().then(r => setErrorText(r.message))
                    }
                })
        }
    }

    function saveLink() {
        updateLink(baseWord.id, linkWord.word.id, params.linkType.typeId, ruleNames)
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
        {linkWord.word.language !== baseWord.language && linkWord.word.language + " "}
        <WordLink word={linkWord.word}/>
        {linkWord.word.gloss != null && ' "' + linkWord.word.gloss + '"' }
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
        </>}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </div>
}

function WordLinkTypeComponent(params) {
    return params.links.map(l => <>
        <div>{l.type}</div>
        {l.words.map(w => <WordLinkComponent baseWord={params.word} linkWord={w} linkType={l} revalidator={params.revalidator}/>)}
        <p/>
    </>)
}

function SingleWord(params) {
    const word = params.word

    const revalidator = useRevalidator()
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const navigate = useNavigate()
    useEffect(() => { document.title = "Etymograph : " + word.text })

    function submitted(r, lr) {
        if (lr && lr.status !== 200) {
            setErrorText(lr.message)
        }
        else {
            setErrorText("")
            setShowBaseWord(false)
            setShowDerivedWord(false)
            setShowCompoundComponent(false)
            setShowRelated(false)
            revalidator.revalidate()
        }
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

    function linkToParseCandidate(pc, wordId) {
        addLink(word.id, wordId, ">", pc.ruleNames.join(","))
            .then((r) => {
                if (r.status !== 200) {
                    setErrorText(r.message)
                }
                revalidator.revalidate()
            })
    }

    function acceptParseCandidate(pc) {
        if (pc.wordId === null) {
            addWord(word.language, pc.text, "", "", null, null)
                .then(r => {
                    if (r.status === 200)
                        r.json().then(r => navigate(`/word/${word.language}/${pc.text}`))
                    else
                        setErrorText(r.message)
                })
        }
        else {
            linkToParseCandidate(pc, pc.wordId)
        }
    }

    return <>
        <h2><small>
            <Link to={`/`}>Etymograph</Link> >{' '}
            <Link to={`/language/${word.language}`}>{word.languageFullName}</Link> >{' '}
            <Link to={`/dictionary/${word.language}`}>Dictionary</Link> > </small>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}/></h2>
        {!editMode && <>
            {word.pos && <div>{word.pos}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? word.fullGloss : word.gloss}</p>
            {word.notes && <p>{word.notes}</p>}
            {word.source != null && <div className="source">Source: {word.source.startsWith("http") ? <a href={word.source}>{word.source}</a> : word.source}</div>}
            {word.parseCandidates.map(pc => <>
                <p>
                    {pc.wordId !== null && <Link to={`/word/${word.language}/${pc.text}/${pc.wordId}`}>{pc.text}</Link>}
                    {pc.wordId === null && <i>{pc.text}</i>}
                    {pc.categories}?{' '}
                    <button onClick={(e) => acceptParseCandidate(pc)}>Accept</button>
                </p>
            </>)}
        </>}
        {editMode && <WordForm language={word.language} updateId={word.id}
                               initialGloss={word.glossComputed ? undefined : word.gloss}
                               initialFullGloss={word.fullGloss}
                               initialPos={word.pos}
                               initialSource={word.source}
                               initialNotes={word.notes}
                               submitted={editSubmitted}/>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>&nbsp;
        {!editMode && <button onClick={() => deleteWordClicked()}>Delete</button>}

        {word.attestations.length > 0 &&
            <p>Attested in {word.attestations.map((att, i) => <>
                {i > 0 && ", "}
                <Link to={`/corpus/text/${att.textId}`}>{att.textTitle}</Link>
                {att.word && ' ("' + att.word + '")'}
                </>
                )}
            </p>
        }

        <WordLinkTypeComponent word={word} links={word.linksFrom} revalidator={revalidator}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} revalidator={revalidator}/>

        <p/>
        <a href="#" onClick={() => setShowBaseWord(!showBaseWord)}>Add base word</a><br/>
        {showBaseWord && <WordForm submitted={submitted} derivedWord={word}/>}
        <a href="#" onClick={() => setShowDerivedWord(!showDerivedWord)}>Add derived word</a><br/>
        {showDerivedWord && <WordForm submitted={submitted} baseWord={word}/>}
        <a href="#" onClick={() => setShowCompoundComponent(!showCompoundComponent)}>Add component of compound</a><br/>
        {showCompoundComponent && <WordForm submitted={submitted} compoundWord={word}/>}
        <a href="#" onClick={() => setShowRelated(!showRelated)}>Add related word</a><br/>
        {showRelated && <WordForm submitted={submitted} relatedWord={word} language={word.language}/>}
        <p/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {word.pos && !word.glossComputed && <Link to={`/word/${word.language}/${word.id}/paradigms`}>Paradigms</Link>}
    </>
}


export default function Word() {
    const words = useLoaderData()
    if (Array.isArray(words)) {
        if (words.length === 1) {
            return <SingleWord word={words[0]}/>
        }
        return <ul>
            {words.map(w => <li><Link to={`/word/${w.language}/${w.text}/${w.id}`}>{w.text} "{w.gloss}"</Link></li>)}
        </ul>
    }
    return <SingleWord word={words}/>
}

export function WordError() {
    return <div>No such word in the dictionary</div>
}
