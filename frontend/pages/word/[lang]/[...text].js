import {useEffect, useState} from "react";
import WordForm from "@/forms/WordForm";
import WordWithStress from "@/components/WordWithStress";
import WordLink from "@/components/WordLink";
import {
    addLink,
    addWord,
    deleteLink,
    deleteWord,
    fetchBackend,
    allowEdit,
    deleteCompound,
    applyRuleSequence, deriveThroughRuleSequence
} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import SourceRefs from "@/components/SourceRefs";
import RuleLinkForm from "@/forms/RuleLinkForm";
import {GlobalStateContext} from "@/components/EtymographForm";
import EditLinkForm from "@/forms/EditLinkForm";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    const params = context.params.text
    if (params.length === 1) {
        return fetchBackend(`word/${context.params.lang}/${params[0]}`, true)
    }
    return fetchBackend(`word/${context.params.lang}/${params[0]}/${params[1]}`, true)
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

    function linkSubmitted() {
        setEditMode(false)
        params.router.replace(params.router.asPath)
    }

    return <div>
        <WordLink word={linkWord.word} baseLanguage={baseWord.language}/>
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
        {editMode && <EditLinkForm
            baseWordId={baseWord.id}
            linkWordId={linkWord.word.id}
            linkType={params.linkType.typeId}
            defaultValues={{
                ruleNames: linkWord.ruleNames.join(","),
                source: linkWord.sourceEditableText
            }}
            submitted={linkSubmitted}
            cancelled={() => setEditMode(false)}/>
        }
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </div>
}

function CompoundRefComponent(params) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord

    return <span>
        <WordLink word={linkWord} baseLanguage={baseWord.language}/>
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
    const [showOriginWord, setShowOriginWord] = useState(false)
    const [showDerivativeWord, setShowDerivativeWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [showVariation, setShowVariation] = useState(false)
    const [showRuleLink, setShowRuleLink] = useState(false)
    const [addToCompound, setAddToCompound] = useState(undefined)
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    useEffect(() => { document.title = "Etymograph : " + (word === undefined ? "Unknown Word" : word.text) })

    function submitted(r) {
        setShowBaseWord(false)
        setShowDerivedWord(false)
        setShowOriginWord(false)
        setShowDerivativeWord(false)
        setShowCompoundComponent(false)
        setShowRelated(false)
        setShowVariation(false)
        setAddToCompound(undefined)
        router.replace(router.asPath)
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

    function ruleLinkSubmitted() {
        setShowRuleLink(false)
        router.replace(router.asPath)
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
            addWord(word.language, pc.text, "", "", pc.pos)
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

    function deriveThroughSequenceClicked(seqId) {
        deriveThroughRuleSequence(word.id, seqId)
            .then(r => {
                if (r.status === 200) {
                    router.replace(router.asPath)
                }
                else {
                    r.json().then(r => setErrorText(r.message))
                }
            })
    }


    if (word === undefined) {
        return <div>No such word in the dictionary</div>
    }

    const isName = word.pos === "NP"
    const isCompound = word.compound
    const posClassesEditable = (word.pos !== null ? word.pos : "") + (word.classes.length > 0 ? " " + word.classes.join(" ") : "")

    return <GlobalStateContext.Provider value={params.globalState}>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${word.language}`}>{word.languageFullName}</Link> {'> '}
            {!isName && !isCompound && <Link href={`/dictionary/${word.language}`}>Dictionary</Link>}
            {isName && <Link href={`/dictionary/${word.language}/names`}>Names</Link>}
            {!isName && isCompound && <Link href={`/dictionary/${word.language}/compounds`}>Compounds</Link>}
            {' > '}</small>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}
                            reconstructed={word.reconstructed || word.languageReconstructed}/></h2>
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
        {editMode && <WordForm
            updateId={word.id}
            defaultValues={{
                language: word.language,
                text: word.text,
                gloss: word.glossComputed ? undefined : word.gloss,
                fullGloss: word.fullGloss,
                posClasses: posClassesEditable,
                reconstructed: word.reconstructed,
                source: word.sourceEditableText,
                notes: word.notes
            }}
            languageReadOnly={true}
            submitted={editSubmitted}
            cancelled={() => setEditMode(false)}
            />}
        {allowEdit() && !editMode && <>
            <p/>
            <button onClick={() => setEditMode(true)}>{"Edit"}</button>&nbsp;
            <button onClick={() => deleteWordClicked()}>Delete</button>
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
                        {m.notes && <> &ndash; {m.notes}</>}
                        <SourceRefs source={m.source} span={true}/>
                        {addToCompound === m.compoundId && <WordForm submitted={submitted} addToCompound={m.compoundId} linkTarget={word} defaultValues={{language: word.language}} globalState={params.globalState}/>}
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
            {showBaseWord && <WordForm submitted={submitted} linkType='>' linkTarget={word} reverseLink={true} defaultValues={{language: word.language, gloss: word.gloss}} cancelled={() => setShowBaseWord(false)}/>}
            <button onClick={() => setShowDerivedWord(!showDerivedWord)}>Add derived word</button><br/>
            {showDerivedWord && <WordForm submitted={submitted} linkType='>' linkTarget={word} defaultValues={{language: word.language}} cancelled={() => setShowDerivedWord(false)} />}
            <button onClick={() => setShowOriginWord(!showOriginWord)}>Add origin word</button><br/>
            {showOriginWord && <WordForm submitted={submitted} linkType='^' linkTarget={word} reverseLink={true} defaultValues={{gloss: word.gloss}} cancelled={() => setShowOriginWord(false)}/>}
            <button onClick={() => setShowDerivativeWord(!showDerivativeWord)}>Add derivative word</button>
            {showDerivativeWord && <WordForm submitted={submitted} linkType='^' linkTarget={word} defaultValues={{gloss: word.gloss}} cancelled={() => setShowDerivativeWord(false)}/>}
            {word.suggestedDeriveSequences.map(seq => <>
                {' '}
                <button className="inlineButton" onClick={() => deriveThroughSequenceClicked(seq.id)}>Derive through {seq.name}</button>
            </>)}
            <br/>
            <button onClick={() => setShowCompoundComponent(!showCompoundComponent)}>Define as compound</button><br/>
            {showCompoundComponent && <WordForm submitted={submitted} newCompound={true} linkTarget={word} defaultValues={{language: word.language}} cancelled={() => setShowCompoundComponent(false)}/>}
            <button onClick={() => setShowRelated(!showRelated)}>Add related word</button><br/>
            {showRelated && <WordForm submitted={submitted} linkType='~' linkTarget={word} defaultValues={{language: word.language}} languageReadOnly={true} cancelled={() => setShowRelated(false)}/>}
            {!isCompound && <><button onClick={() => setShowVariation(!showVariation)}>Add variation of</button><br/></>}
            {showVariation && <WordForm submitted={submitted} linkType='=' reverseLink={true} linkTarget={word} defaultValues={{language: word.language}} languageReadOnly={true} cancelled={() => setShowVariation(false)}/>}
            <button onClick={() => setShowRuleLink(!showRuleLink)}>Add related rule</button><br/>
            {showRuleLink && <RuleLinkForm submitted={ruleLinkSubmitted} fromEntityId={word.id}/>}
            <p/>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </>}
        {word.pos && (!word.glossComputed || word.pos === 'NP') && <Link href={`/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}
    </GlobalStateContext.Provider>
}


export default function Word(params) {
    const words = params.loaderData
    if (Array.isArray(words)) {
        if (words.length === 1) {
            return <SingleWord word={words[0]} globalState={params.globalState}/>
        }
        return <ul>
            {words.map(w => <li key={w.id}><Link href={`/word/${w.language}/${w.text}/${w.id}`}>{w.text} &quot;{w.gloss}&quot;</Link></li>)}
        </ul>
    }
    return <SingleWord word={words} globalState={params.globalState}/>
}
