import {useEffect, useState} from "react";
import WordForm from "@/components/WordForm";
import WordWithStress from "@/components/WordWithStress";
import WordLink from "@/components/WordLink";
import {
    addLink,
    addWord,
    deleteLink,
    deleteWord,
    fetchBackend,
    allowEdit,
    updateLink,
    deleteCompound,
    applyRuleSequence
} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import SourceRefs from "@/components/SourceRefs";
import RuleLinkForm from "@/components/RuleLinkForm";

export const config = {
    unstable_runtimeJS: true
}

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
            paths.push({params: {lang: lang.shortName, text: [word.text.toLowerCase()]}})
            if (word.homonym) {
                paths.push({params: {lang: lang.shortName, text: [word.text.toLowerCase(), word.id.toString()]}})
            }
        }
    }
    return {paths, fallback: allowEdit()}
}

function WordLinkComponent(params) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord
    const [editMode, setEditMode] = useState(false)
    const [ruleNames, setRuleNames] = useState(linkWord.ruleNames.join(","))
    const [source, setSource] = useState(linkWord.sourceEditableText)
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

    function applySequenceClicked(seqId, fromWordId, toWordId) {
        applyRuleSequence(seqId, fromWordId, toWordId)
            .then((response) => {
                if (response.status === 200) {
                    response.json().then(r => {
                        if (r.ruleIds.length > 0) {
                            params.router.replace(params.router.asPath)
                        }
                        else {
                            setErrorText("No matching rules in this rule sequence")
                        }
                    })
                }
                else {
                    response.json().then(r => setErrorText(r.message))
                }
            })
    }

    function saveLink() {
        updateLink(baseWord.id, linkWord.word.id, params.linkType.typeId, ruleNames, source)
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
            {linkWord.ruleResults.length > 0 && <>
                {params.directionFrom ? linkWord.word.text : baseWord.text}
                {linkWord.ruleIds.map((ruleId, index) => <>
                    {' '}<Link href={`/rule/${ruleId}`} title={linkWord.ruleNames[index]}>{'>'}</Link>{' '}
                    {linkWord.ruleResults[index]}
                </>)}
            </>}
            {linkWord.ruleResults.length === 0 && linkWord.ruleIds.map((ruleId, index) => <>
                {index > 0 && ", "}
                <Link href={`/rule/${ruleId}`}>{linkWord.ruleNames[index]}</Link>
            </>)}
            )</>}
        <SourceRefs source={linkWord.source} span={true}/>
        {allowEdit() && <>
            &nbsp;<span className="inlineButtonLink">
                    (<button className="inlineButton" onClick={() => setEditMode(!editMode)}>edit</button>
                </span>
            &nbsp;|&nbsp;<span className="inlineButtonLink">
                    <button className="inlineButton" onClick={() => deleteLinkClicked()}>delete</button>
                </span>
            {linkWord.suggestedSequences.map(seq => <>
                &nbsp;|&nbsp;<span className="inlineButtonLink">
                    <button className="inlineButton" onClick={() => applySequenceClicked(seq.id, baseWord.id, linkWord.word.id)}>apply sequence {seq.name}</button>
                </span>
            </>)}
            )
        </>}
        {editMode && <>
            <table>
                <tbody>
                <tr>
                <td>Rule names:</td>
                <td><input type="text" value={ruleNames} onChange={e => setRuleNames(e.target.value)}/></td>
            </tr>
            <tr>
                <td>Source</td>
                <td><input type="text" value={source} onChange={e => setSource(e.target.value)}/></td>
            </tr>
            </tbody></table>
            <button onClick={() => saveLink()}>Save</button>&nbsp;
            <button onClick={() => setEditMode(false)}>Cancel</button>
        </>}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </div>
}

function CompoundRefComponent(params) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord

    return <span>
        {linkWord.language !== baseWord.language && linkWord.language + " "}
        <WordLink word={linkWord}/>
        {linkWord.gloss != null && ' "' + linkWord.gloss + '"' }
    </span>
}

function WordLinkTypeComponent(params) {
    return params.links.map(l => <>
        <div>{l.type}</div>
        {l.words.map(w => <WordLinkComponent key={w.id} baseWord={params.word} linkWord={w} linkType={l}
                                             router={params.router} directionFrom={params.directionFrom}/>)}
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
    const [showVariation, setShowVariation] = useState(false)
    const [showRuleLink, setShowRuleLink] = useState(false)
    const [addToCompound, setAddToCompound] = useState(undefined)
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    useEffect(() => { document.title = "Etymograph : " + (word === undefined ? "Unknown Word" : word.text) })

    function submitted(status, r, lr) {
        if (lr && lr.status !== 200) {
            setErrorText(lr.message)
        }
        else {
            setShowBaseWord(false)
            setShowDerivedWord(false)
            setShowCompoundComponent(false)
            setShowRelated(false)
            setShowVariation(false)
            setAddToCompound(undefined)
            if (status !== 200) {
                setErrorText(r.message)
            }
            else {
                setErrorText("")
                router.replace(router.asPath)
            }
        }
    }

    function editSubmitted(status, r) {
        if (status !== 200) {
            setErrorText(r.message)
        }
        else {
            setEditMode(false)
            if (r.text !== word.text) {
                router.push(`/word/${word.language}/${r.text}`)
            }
            else {
                router.replace(router.asPath)
            }
        }
    }

    function ruleLinkSubmitted(status, r) {
        if (status !== 200) {
            setErrorText(r.message)
        }
        else {
            setShowRuleLink(false)
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

    function deleteCompoundClicked(compoundId) {
        if (window.confirm("Delete this compound?")) {
            deleteCompound(compoundId).then(() => router.replace(router.asPath))
        }
    }

    function deleteRuleLinkClicked(ruleId, linkType) {
        if (window.confirm("Delete this link?")) {
            deleteLink(word.id, ruleId, linkType)
                .then(r => {
                    if (r.status === 200) {
                        router.replace(router.asPath)
                    }
                    else {
                        r.json().then(r => setErrorText(r.message))
                    }
                })
        }
    }

    if (word === undefined) {
        return <div>No such word in the dictionary</div>
    }

    const isName = word.pos === "NP"
    const isCompound = word.compound
    const posClassesEditable = (word.pos !== null ? word.pos : "") + (word.classes.length > 0 ? " " + word.classes.join(" ") : "")

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${word.language}`}>{word.languageFullName}</Link> {'> '}
            {!isName && !isCompound && <Link href={`/dictionary/${word.language}`}>Dictionary</Link>}
            {isName && <Link href={`/dictionary/${word.language}/names`}>Names</Link>}
            {!isName && isCompound && <Link href={`/dictionary/${word.language}/compounds`}>Compounds</Link>}
            {' > '}</small>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}/></h2>
        {!editMode && <>
            {word.pos && <div>{word.pos} {word.classes.length > 0 && "(" + word.classes.join(", ") + ")"}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? word.fullGloss : word.gloss}</p>
            {word.notes && <p>{word.notes}</p>}
            <SourceRefs source={word.source}/>
            {allowEdit() && word.parseCandidates.map(pc => <>
                <p>
                    {pc.wordId !== null && <Link href={`/word/${word.language}/${pc.text}/${pc.wordId}`}>{pc.text}</Link>}
                    {pc.wordId === null && <i>{pc.text}</i>}
                    {pc.categories.length === 0 && ` (${pc.ruleNames.join(",")})`}
                    {pc.categories}?{' '}
                    <button onClick={() => acceptParseCandidate(pc)}>Accept</button>
                </p>
            </>)}
        </>}
        {editMode && <WordForm language={word.language} languageReadOnly={true}
                               updateId={word.id}
                               initialText={word.text}
                               initialGloss={word.glossComputed ? undefined : word.gloss}
                               initialFullGloss={word.fullGloss}
                               initialPosClasses={posClassesEditable}
                               initialSource={word.sourceEditableText}
                               initialNotes={word.notes}
                               submitted={editSubmitted}/>}
        {allowEdit() && <>
            <p/>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>&nbsp;
            {!editMode && <button onClick={() => deleteWordClicked()}>Delete</button>}
        </>}

        {word.attestations.length > 0 &&
            <p>Attested in {word.attestations.map((att, i) => <>
                    {i > 0 && ", "}
                    <Link href={`/corpus/text/${att.textId}`}>{att.textTitle}</Link>
                    {att.word && ' ("' + att.word + '")'}
                </>
            )}
            </p>
        }

        <WordLinkTypeComponent word={word} links={word.linksFrom} directionFrom={true} router={router}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} directionFrom={false} router={router}/>

        {word.compounds.length > 0 &&
            <>
                <div>Component of compounds</div>
                {word.compounds.map(m => <div><CompoundRefComponent key={m.id} baseWord={params.word} linkWord={m} router={params.router}/></div>)}
                <p/>
            </>
        }
        {word.components.length > 0 &&
            <>
                <div>Compound:</div>
                {word.components.map(m =>
                    <div>
                        {m.components.map((mc, index) => <>
                            {index > 0 && " + "}
                            <CompoundRefComponent key={mc.id} baseWord={params.word} linkWord={mc} router={params.router}/>
                        </>)}
                        <SourceRefs source={m.source} span={true}/>
                        {addToCompound === m.compoundId && <WordForm submitted={submitted} addToCompound={m.compoundId} linkTarget={word} language={word.language} />}
                        {allowEdit() && <>
                            {' '}
                            {addToCompound === m.compoundId
                                ? <button onClick={() => setAddToCompound(undefined)}>Cancel</button>
                                : <button onClick={() => setAddToCompound(m.compoundId)}>Add component</button>}
                            {' '}
                            <button onClick={() => deleteCompoundClicked(m.compoundId)}>Delete</button>
                        </>}
                    </div>
                )}
            </>
        }

        {word.linkedRules.length > 0 &&
            <>
                <div>Linked rules:</div>
                {word.linkedRules.map(rl => <>
                    <Link href={`/rule/${rl.ruleId}`}>{rl.ruleName}</Link>
                    <SourceRefs source={rl.source} span={true}/>
                    {allowEdit() && <>
                        &nbsp;(<span className="inlineButtonLink">
                            <button className="inlineButton" onClick={() => deleteRuleLinkClicked(rl.ruleId, rl.linkType)}>delete</button>
                        </span>)</>
                    }
                    <br/></>
                )}
            </>
        }

        <p/>
        {allowEdit() && <>
            {!isCompound && <><button onClick={() => setShowBaseWord(!showBaseWord)}>Add base word</button><br/></>}
            {showBaseWord && <WordForm submitted={submitted} linkType='>' linkTarget={word} reverseLink={true} language={word.language} />}
            <button onClick={() => setShowDerivedWord(!showDerivedWord)}>Add derived word</button><br/>
            {showDerivedWord && <WordForm submitted={submitted} linkType='>' linkTarget={word} language={word.language} />}
            <button onClick={() => setShowCompoundComponent(!showCompoundComponent)}>Define as compound</button><br/>
            {showCompoundComponent && <WordForm submitted={submitted} newCompound={true} linkTarget={word} language={word.language} />}
            <button onClick={() => setShowRelated(!showRelated)}>Add related word</button><br/>
            {showRelated && <WordForm submitted={submitted} linkType='~' linkTarget={word} language={word.language} languageReadOnly={true}/>}
            {!isCompound && <><button onClick={() => setShowVariation(!showVariation)}>Add variation of</button><br/></>}
            {showVariation && <WordForm submitted={submitted} linkType='=' reverseLink={true} linkTarget={word} language={word.language} languageReadOnly={true}/>}
            <button onClick={() => setShowRuleLink(!showRuleLink)}>Add related rule</button><br/>
            {showRuleLink && <RuleLinkForm submitted={ruleLinkSubmitted} fromEntityId={word.id}/>}
            <p/>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </>}
        {word.pos && (!word.glossComputed || word.pos === 'NP') && <Link href={`/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}
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
