import {useEffect, useState} from "react";
import WordForm from "@/components/WordForm";
import WordWithStress from "@/components/WordWithStress";
import WordLink from "@/components/WordLink";
import {addLink, addWord, deleteLink, deleteWord, fetchBackend, updateLink} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";

export async function getStaticProps(context) {
    const params = context.params.text
    if (params.length === 1) {
        return fetchBackend(`word/${context.params.lang}/${params[0]}`)
    }
    return fetchBackend(`word/${context.params.lang}/${params[0]}/${params[1]}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`language`)
    const paths = []
    for (const lang of props.loaderData) {
        let url = `dictionary/${lang.shortName}/all`
        const dictData = await fetchBackend(url)
        for (const word of dictData.props.loaderData.words) {
            paths.push({params: {lang: lang.shortName, text: [word.text]}})
            if (word.homonym) {
                paths.push({params: {lang: lang.shortName, text: [word.text, word.id.toString()]}})
            }
        }
    }
    return {paths, fallback: false}
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
                        params.router.replace(params.router.asPath)
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
                    params.router.replace(params.router.asPath)
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
                <Link href={`/rule/${ruleId}`}>{linkWord.ruleNames[index]}</Link>
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
        {l.words.map(w => <WordLinkComponent key={w.id} baseWord={params.word} linkWord={w} linkType={l} router={params.router}/>)}
        <p/>
    </>)
}

function SingleWord(params) {
    const word = params.word

    const router = useRouter()
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
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
            router.replace(router.asPath)
        }
    }

    function editSubmitted(r) {
        setEditMode(false)
        if (r.text !== word.text) {
            router.push(`/word/${word.language}/${r.text}`)
        }
        else {
            router.replace(router.asPath)
        }
    }

    function deleteWordClicked() {
        if (window.confirm("Delete this word?")) {
            deleteWord(word.id)
                .then(() => router.push("/dictionary/" + word.language))
        }
    }

    function linkToParseCandidate(pc, wordId) {
        addLink(word.id, wordId, ">", pc.ruleNames.join(","))
            .then((r) => {
                if (r.status !== 200) {
                    setErrorText(r.message)
                }
                router.replace(router.asPath)
            })
    }

    function acceptParseCandidate(pc) {
        if (pc.wordId === null) {
            addWord(word.language, pc.text, "", "", pc.pos, null)
                .then(r => {
                    if (r.status === 200)
                        r.json().then(r =>
                            linkToParseCandidate(pc, r.id)
                        )
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
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${word.language}`}>{word.languageFullName}</Link> {'> '}
            <Link href={`/dictionary/${word.language}`}>Dictionary</Link> {'>'} </small>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}/></h2>
        {!editMode && <>
            {word.pos && <div>{word.pos}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? word.fullGloss : word.gloss}</p>
            {word.notes && <p>{word.notes}</p>}
            {word.source != null && <div className="source">Source: {word.source.startsWith("http") ? <a href={word.source}>{word.source}</a> : word.source}</div>}
            {word.parseCandidates.map(pc => <>
                <p>
                    {pc.wordId !== null && <Link href={`/word/${word.language}/${pc.text}/${pc.wordId}`}>{pc.text}</Link>}
                    {pc.wordId === null && <i>{pc.text}</i>}
                    {pc.categories.length === 0 && ` (${pc.ruleNames.join(",")})`}
                    {pc.categories}?{' '}
                    <button onClick={(e) => acceptParseCandidate(pc)}>Accept</button>
                </p>
            </>)}
        </>}
        {editMode && <WordForm language={word.language} updateId={word.id}
                               initialWord={word.text}
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
                    <Link href={`/corpus/text/${att.textId}`}>{att.textTitle}</Link>
                    {att.word && ' ("' + att.word + '")'}
                </>
            )}
            </p>
        }

        <WordLinkTypeComponent word={word} links={word.linksFrom} router={router}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} router={router}/>

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
        {word.pos && !word.glossComputed && <Link href={`/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}
    </>
}


export default function Word(params) {
    const words = params.loaderData
    if (Array.isArray(words)) {
        if (words.length === 1) {
            return <SingleWord word={words[0]}/>
        }
        return <ul>
            {words.map(w => <li key={w.id}><Link href={`/word/${w.language}/${w.text}/${w.id}`}>{w.text} &quot;{w.gloss}&quot;</Link></li>)}
        </ul>
    }
    return <SingleWord word={words}/>
}

export function WordError() {
    return <div>No such word in the dictionary</div>
}
