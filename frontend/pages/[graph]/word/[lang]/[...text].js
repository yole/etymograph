import {useContext, useEffect, useState} from "react";
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
    applyRuleSequence, deriveThroughRuleSequence, fetchAllLanguagePaths
} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import SourceRefs from "@/components/SourceRefs";
import RuleLinkForm from "@/forms/RuleLinkForm";
import EditLinkForm from "@/forms/EditLinkForm";
import {GraphContext} from "@/components/Contexts";
import Breadcrumbs from "@/components/Breadcrumbs";
import WordGloss from "@/components/WordGloss";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    const params = context.params.text
    if (params.length === 1) {
        return fetchBackend(context.params.graph, `word/${context.params.lang}/${params[0]}`, true)
    }
    return fetchBackend(context.params.graph, `word/${context.params.lang}/${params[0]}/${params[1]}`, true)
}

export async function getStaticPaths() {
    const langPaths = await fetchAllLanguagePaths()
    const paths = []
    for (const path of langPaths.paths) {
        let url = `dictionary/${path.params.lang}/all`
        const dictData = await fetchBackend(path.params.graph, url)
        for (const word of dictData.props.loaderData.words) {
            paths.push({params: {...path.params, text: [word.text.toLowerCase()]}})
            if (word.homonym) {
                paths.push({params: {...path.params, text: [word.text.toLowerCase(), word.id.toString()]}})
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
    const router = useRouter()
    const graph = router.query.graph

    async function deleteLinkClicked() {
        if (window.confirm("Delete this link?")) {
            const r = await deleteLink(graph, baseWord.id, linkWord.word.id, params.linkType.typeId)
            if (r.status === 200) {
                setErrorText("")
                router.replace(router.asPath)
            }
            else {
                r.json().then(r => setErrorText(r.message))
            }
        }
    }

    async function applySequenceClicked(seqId, fromWordId, toWordId) {
        const response = await applyRuleSequence(graph, seqId, fromWordId, toWordId)
        if (response.status === 200) {
            const r = await response.json()
            if (r.ruleIds.length > 0) {
                router.replace(router.asPath)
            }
            else {
                setErrorText("No matching rules in this rule sequence")
            }
        }
        else {
            response.json().then(r => setErrorText(r.message))
        }
    }

    function linkSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    return <div>
        <WordLink word={linkWord.word} baseLanguage={baseWord.language} gloss={true}/>
        {linkWord.ruleIds.length > 0 && <>&nbsp;(
            {linkWord.ruleResults.length > 0 && <>
                {params.directionFrom ? linkWord.word.text : baseWord.text}
                {linkWord.ruleIds.map((ruleId, index) => <>
                    {' '}<Link href={`/${graph}/rule/${ruleId}`} title={linkWord.ruleNames[index]}>{'>'}</Link>{' '}
                    {linkWord.ruleResults[index]}
                </>)}
            </>}
            {linkWord.ruleResults.length === 0 && linkWord.ruleIds.map((ruleId, index) => <>
                {index > 0 && ", "}
                <Link href={`/${graph}/rule/${ruleId}`}>{linkWord.ruleNames[index]}</Link>
            </>)}
            )</>}
        {linkWord.notes && <> &ndash; {linkWord.notes}</>}
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
                source: linkWord.sourceEditableText,
                notes: linkWord.notes
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

    return <WordLink word={linkWord} baseLanguage={baseWord.language} gloss={true}/>
}

function WordLinkTypeComponent(params) {
    return params.links.map(l => <>
        <div>{l.type}</div>
        {l.words.map(w => <WordLinkComponent key={w.id} baseWord={params.word} linkWord={w} linkType={l}
                                             directionFrom={params.directionFrom}/>)}
        <p/>
    </>)
}

function SingleWord(params) {
    const word = params.word

    const router = useRouter()
    const graph = router.query.graph
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showOriginWord, setShowOriginWord] = useState(false)
    const [showDerivativeWord, setShowDerivativeWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [showVariation, setShowVariation] = useState(false)
    const [showRuleLink, setShowRuleLink] = useState(false)
    const [addToCompound, setAddToCompound] = useState(undefined)
    const [editCompound, setEditCompound] = useState(undefined)
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
        setEditCompound(undefined)
        router.replace(router.asPath)
    }

    function editSubmitted(r) {
        setEditMode(false)
        if (r.text !== word.text) {
            router.push(`/${graph}/word/${word.language}/${r.text}`)
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
            deleteWord(graph, word.id)
                .then(() => router.push(`/${graph}/dictionary/${word.language}`))
        }
    }

    async function linkToParseCandidate(pc, wordId) {
        const r = await addLink(graph, word.id, wordId, ">", pc.ruleNames.join(","))
        if (r.status !== 200) {
            setErrorText(r.message)
        }
        router.replace(router.asPath)
    }

    async function acceptParseCandidate(pc) {
        if (pc.wordId === null) {
            const r = await addWord(graph, word.language, pc.text, "", "", pc.pos)
            if (r.status === 200)
                r.json().then(r =>
                    linkToParseCandidate(pc, r.id)
                )
            else
                setErrorText(r.message)
        }
        else {
            linkToParseCandidate(pc, pc.wordId)
        }
    }

    function deleteCompoundClicked(compoundId) {
        if (window.confirm("Delete this compound?")) {
            deleteCompound(graph, compoundId).then(() => router.replace(router.asPath))
        }
    }

    async function deleteRuleLinkClicked(ruleId, linkType) {
        if (window.confirm("Delete this link?")) {
            const r = await deleteLink(graph, word.id, ruleId, linkType)
            if (r.status === 200) {
                router.replace(router.asPath)
            }
            else {
                r.json().then(r => setErrorText(r.message))
            }
        }
    }

    async function deriveThroughSequenceClicked(seqId) {
        const r = await deriveThroughRuleSequence(graph, word.id, seqId)
        if (r.status === 200) {
            router.replace(router.asPath)
        }
        else {
            r.json().then(r => setErrorText(r.message))
        }
    }


    if (word === undefined) {
        return <div>No such word in the dictionary</div>
    }

    const isName = word.pos === "NP"
    const isCompound = word.compound
    const posClassesEditable = (word.pos !== null ? word.pos : "") + (word.classes.length > 0 ? " " + word.classes.join(" ") : "")

    const [dictionaryTitle, dictionaryLink] =
        isName ? ["Names", "/names"] :
            (isCompound ? ["Compounds", "/compounds"] : ["Dictionary", ""])

    return <>
        <Breadcrumbs langId={word.language} langName={word.languageFullName}
                     steps={[{title: dictionaryTitle, url: `/${graph}/dictionary/${word.language}${dictionaryLink}`}]}>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}
                            reconstructed={word.reconstructed || word.languageReconstructed}/>
        </Breadcrumbs>

        {!editMode && <>
            {word.pos && <div>{word.pos} {word.classes.length > 0 && "(" + word.classes.join(", ") + ")"}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? word.fullGloss : <WordGloss gloss={word.gloss}/>}</p>
            {word.notes && <p>{word.notes}</p>}
            <SourceRefs source={word.source}/>
            {allowEdit() && word.parseCandidates.map(pc => <>
                <p>
                    {pc.wordId !== null && <Link href={`/${graph}/word/${word.language}/${pc.text}/${pc.wordId}`}>{pc.text}</Link>}
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
                text: word.textWithExplicitStress,
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
                    <Link href={`/${graph}/corpus/text/${att.textId}`}>{att.textTitle}</Link>
                    {att.word && ' ("' + att.word + '")'}
                </>
            )}
            </p>
        }

        <WordLinkTypeComponent graph={graph} word={word} links={word.linksFrom} directionFrom={true}/>
        <WordLinkTypeComponent graph={graph} word={word} links={word.linksTo} directionFrom={false}/>

        {word.compounds.length > 0 &&
            <>
                <div>Component of compounds</div>
                {word.compounds.map(m => <div><CompoundRefComponent graph={graph} key={m.id} baseWord={params.word} linkWord={m} router={params.router}/></div>)}
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
                        {addToCompound === m.compoundId &&
                            <WordForm submitted={submitted} cancelled={() => setAddToCompound(undefined)}
                                      addToCompound={m.compoundId} linkTarget={word} defaultValues={{language: word.language}}/>
                        }
                        {editCompound === m.compoundId &&
                            <EditLinkForm compoundId={m.compoundId}
                                          defaultValues={{source: m.sourceEditableText, notes: m.notes}}
                                          submitted={() => {
                                              setEditCompound(undefined)
                                              router.replace(router.asPath)
                                          }}
                                          cancelled={() => setEditCompound(undefined)}/>
                        }
                        {allowEdit() && <>
                            {' '}
                            {addToCompound !== m.compoundId && <button onClick={() => setAddToCompound(m.compoundId)}>Add component</button>}
                            {' '}
                            {editCompound !== m.compoundId && <button onClick={() => setEditCompound(m.compoundId)}>Edit compound</button>}
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
                    <Link href={`/${graph}/rule/${rl.ruleId}`}>{rl.ruleName}</Link>
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
        {word.pos && (!word.glossComputed || word.pos === 'NP') && <Link href={`/${graph}/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}
    </>
}

export default function Word(params) {
    const words = params.loaderData
    const graph = useContext(GraphContext)
    if (Array.isArray(words)) {
        if (words.length === 1) {
            return <SingleWord word={words[0]}/>
        }
        return <ul>
            {words.map(w => <li key={w.id}><Link href={`/${graph}/word/${w.language}/${w.text}/${w.id}`}>{w.text} &quot;{w.gloss}&quot;</Link></li>)}
        </ul>
    }
    return <SingleWord word={words}/>
}
