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
    applyRuleSequence,
    deriveThroughRuleSequence,
    fetchAllLanguagePaths,
    lookupWord,
    suggestParseCandidates,
    suggestCompound,
    createCompound,
    callApiAndRefresh,
    addToCompound,
    updateWord,
    refreshLinkSequence
} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import SourceRefs from "@/components/SourceRefs";
import RuleLinkForm from "@/forms/RuleLinkForm";
import EditLinkForm from "@/forms/EditLinkForm";
import {GlobalStateContext, GraphContext} from "@/components/Contexts";
import Breadcrumbs from "@/components/Breadcrumbs";
import WordGloss, {WordFullGloss} from "@/components/WordGloss";
import {
    CompoundComponentsViewModel, LinkTypeViewModel, LinkWordViewModel,
    LookupVariantViewModel,
    ParseCandidateViewModel, WordRefViewModel,
    WordViewModel
} from "@/models";
import {set} from "react-hook-form";

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

interface WordLinkProps {
    baseWord: { id: number, language: string, text: string }
    linkWord: LinkWordViewModel
    linkType: { typeId: string, type: string }
    showType?: boolean
    showSequence?: boolean
    directionFrom: boolean
    linkClassName?: string
}

export function WordLinkComponent(params: WordLinkProps) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph as string

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

    async function applySequenceClicked(seqId: number, fromWordId: number, toWordId: number) {
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

    async function refreshSequenceClicked() {
        callApiAndRefresh(() => refreshLinkSequence(graph, baseWord.id, linkWord.word.id, params.linkType.typeId),
            router, setErrorText)
    }

    function linkSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    return <div className={params.linkClassName}>
        {params.showType && params.linkType.type + ": "}
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
        {params.showSequence && linkWord.ruleSequence && <>&nbsp;through {linkWord.ruleSequence.name}</>}
        {linkWord.notes && <> &ndash; {linkWord.notes}</>}
        <SourceRefs source={linkWord.source} span={true}/>
        {allowEdit() && <>
            &nbsp;<span className="inlineButtonLink">
                    (<button className="inlineButton" onClick={() => setEditMode(!editMode)}>edit</button>
                </span>
            &nbsp;|&nbsp;<span className="inlineButtonLink">
                    <button className="inlineButton" onClick={() => deleteLinkClicked()}>delete</button>
                </span>
            {params.showSequence && linkWord.ruleSequence && <>
                &nbsp;|&nbsp;<span className="inlineButtonLink">
                    <button className="inlineButton" onClick={() => refreshSequenceClicked()}>refresh</button>
                </span>
            </>}
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
            language={baseWord.language === linkWord.word.language ? baseWord.language : undefined}
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

function CompoundRefComponent(params: {baseWord: WordViewModel, linkWord: WordRefViewModel}) {
    const {baseWord, linkWord} = params
    return <WordLink word={linkWord} baseLanguage={baseWord.language} gloss={true}/>
}

function CompoundListComponent(
    {compounds, word, derivation}: {compounds: CompoundComponentsViewModel[], word: WordViewModel, derivation: boolean}
) {
    const router = useRouter()
    const graph = router.query.graph as string

    const [addToCompoundId, setAddToCompoundId] = useState(undefined)
    const [editCompound, setEditCompound] = useState(undefined)
    const [compoundSuggestions, setCompoundSuggestions] = useState([] as WordRefViewModel[])
    const [errorText, setErrorText] = useState("")

    async function prepareAddToCompound(compoundId: number) {
        setAddToCompoundId(compoundId)
        const r = await suggestCompound(graph, word.id, compoundId)
        if (r.ok()) {
            const jr = await r.result()
            setCompoundSuggestions(jr.suggestions)
        }
    }

    async function acceptCompoundSuggestion(id: number) {
        const compoundId = addToCompoundId
        setAddToCompoundId(undefined)
        callApiAndRefresh(() => addToCompound(graph, compoundId, id, true),
            router, setErrorText)
    }

    function deleteCompoundClicked(compoundId: number) {
        if (window.confirm("Delete this compound?")) {
            deleteCompound(graph, compoundId).then(() => router.replace(router.asPath))
        }
    }

    function submitted() {
        setAddToCompoundId(undefined)
        setEditCompound(undefined)
        router.replace(router.asPath)
    }

    return <>
        <div>{derivation ? "Derived with affix from:" : "Compound:"}</div>
        {compounds.map(m =>
            <div>
                {m.components.map((mc, index) => <>
                    {index > 0 && " + "}
                    <CompoundRefComponent key={mc.id} baseWord={word} linkWord={mc}/>
                    {index === m.headIndex && " (head)"}
                </>)}
                {m.notes && <> &ndash; {m.notes}</>}
                <SourceRefs source={m.source} span={true}/>
                {addToCompoundId === m.compoundId && <>
                    {compoundSuggestions.length > 0 && <br/>}
                    {compoundSuggestions.map(c => <>
                        <button className="inlineButton" onClick={() => acceptCompoundSuggestion(c.id)}>
                            {c.text}{c.homonym && " '" + c.gloss + "'"}
                        </button>{' '}
                    </>)}
                    {compoundSuggestions.length > 0 && <br/>}
                    <WordForm wordSubmitted={submitted} cancelled={() => setAddToCompoundId(undefined)}
                              addToCompound={m.compoundId} linkTarget={word} defaultValues={{language: word.language}}/>
                </>}
                {editCompound === m.compoundId &&
                    <EditLinkForm compoundId={m.compoundId}
                                  compoundComponents={m.components}
                                  compoundHead={m.headIndex === null ? -1 : m.components[m.headIndex].id}
                                  defaultValues={{source: m.sourceEditableText, notes: m.notes}}
                                  submitted={() => {
                                      setEditCompound(undefined)
                                      router.replace(router.asPath)
                                  }}
                                  cancelled={() => setEditCompound(undefined)}/>
                }
                {allowEdit() && <>
                    {' '}
                    {addToCompoundId !== m.compoundId && <button onClick={() => prepareAddToCompound(m.compoundId)}>Add component</button>}
                    {' '}
                    {editCompound !== m.compoundId && <button onClick={() => setEditCompound(m.compoundId)}>Edit compound</button>}
                    {' '}
                    <button onClick={() => deleteCompoundClicked(m.compoundId)}>Delete</button>
                </>}
            </div>
        )}
    </>
}

interface WordLinkTypeProps {
    word: WordViewModel
    links: LinkTypeViewModel[]
    directionFrom: boolean
}

function WordLinkTypeComponent(params: WordLinkTypeProps) {
    return <>{params.links.map(l => <>
        {l.words.length === 1 && params.directionFrom &&
            <WordLinkComponent key={l.words[0].word.id} baseWord={params.word} linkWord={l.words[0]} linkType={l}
                               directionFrom={params.directionFrom} showType={true} showSequence={true}/>}
        {(l.words.length !== 1 || !params.directionFrom) && <div>
            <div>{l.type}</div>
            {l.words.map(w => <WordLinkComponent key={w.word.id} baseWord={params.word} linkWord={w} linkType={l}
                                                 directionFrom={params.directionFrom} showSequence={true}/>)}
        </div>}
    </>)}</>
}

function SingleWord({word}: { word: WordViewModel }) {
    const globalState = useContext(GlobalStateContext)

    const router = useRouter()
    const graph = router.query.graph as string
    const [showBaseWord, setShowBaseWord] = useState(false)
    const [showDerivedWord, setShowDerivedWord] = useState(false)
    const [showOriginWord, setShowOriginWord] = useState(false)
    const [showDerivativeWord, setShowDerivativeWord] = useState(false)
    const [showCompoundComponent, setShowCompoundComponent] = useState(false)
    const [showRelated, setShowRelated] = useState(false)
    const [showVariationOf, setShowVariationOf] = useState(false)
    const [showVariation, setShowVariation] = useState(false)
    const [showRuleLink, setShowRuleLink] = useState(false)
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [lookupErrorText, setLookupErrorText] = useState("")
    const [lookupVariants, setLookupVariants] = useState([] as LookupVariantViewModel[])
    const [parseCandidates, setParseCandidates] = useState([] as ParseCandidateViewModel[])
    const [compoundSuggestions, setCompoundSuggestions] = useState([] as WordRefViewModel[])
    useEffect(() => { document.title = "Etymograph : " + (word === undefined ? "Unknown Word" : word.text) })

    function submitted() {
        setShowBaseWord(false)
        setShowDerivedWord(false)
        setShowOriginWord(false)
        setShowDerivativeWord(false)
        setShowCompoundComponent(false)
        setShowRelated(false)
        setShowVariationOf(false)
        setShowVariation(false)
        router.replace(router.asPath)
    }

    function editSubmitted(r: WordViewModel) {
        setEditMode(false)
        setLookupErrorText(null)
        setLookupVariants([])
        if (r.text !== word.text) {
            router.push(`/${graph}/word/${word.language}/${r.text}`)
        }
        else {
            router.replace(router.asPath)
        }
    }

    async function lookupWordClicked(disambiguation: string = null) {
        const r = await lookupWord(graph, word.id, {dictionaryId: "wiktionary", disambiguation})
        if (!r.ok()) {
            const error = await r.error()
            setLookupErrorText(error)
            setLookupVariants([])
        }
        else {
            const jr = await r.result()
            if (jr.status !== null) {
                setLookupErrorText(jr.status)
                setLookupVariants(jr.variants)
            }
            else {
                setLookupErrorText(null)
                setLookupVariants([])
                router.replace(router.asPath)
            }
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

    async function suggestParseCandidatesClicked() {
        const r = await suggestParseCandidates(graph, word.id)
        const jr = await r.json()
        setParseCandidates(jr.parseCandidates)
    }

    async function linkToParseCandidate(pc: ParseCandidateViewModel, wordId: number) {
        const r = await addLink(graph, word.id, wordId, ">", pc.ruleNames.join(","))
        if (r.status !== 200) {
            const jr = await r.json()
            setErrorText(jr.message)
        }
        setParseCandidates([])
        router.replace(router.asPath)
    }

    async function acceptParseCandidate(pc: ParseCandidateViewModel) {
        if (pc.wordId === null) {
            const r = await addWord(graph, word.language, pc.text, "", "", pc.pos)
            if (r.status === 200)
                r.json().then(r =>
                    linkToParseCandidate(pc, r.id)
                )
            else {
                const jr = await r.json()
                setErrorText(jr.message)
            }
        }
        else {
            linkToParseCandidate(pc, pc.wordId)
        }
    }

    async function defineAsCompoundClicked() {
        let newState = !showCompoundComponent
        setShowCompoundComponent(newState)
        if (newState) {
            const r = await suggestCompound(graph, word.id)
            if (r.ok()) {
                const jr = await r.result()
                setCompoundSuggestions(jr.suggestions)
            }
        }
    }

    async function acceptCompoundSuggestion(id: number) {
        setShowCompoundComponent(false)
        callApiAndRefresh(() => createCompound(graph, word.id, id), router, setErrorText)
    }

    async function deleteRuleLinkClicked(ruleId: number, linkType: string) {
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

    async function deriveThroughSequenceClicked(seqId: number) {
        const r = await deriveThroughRuleSequence(graph, word.id, seqId)
        if (r.status === 200) {
            router.replace(router.asPath)
        }
        else {
            r.json().then(r => setErrorText(r.message))
        }
    }

    function canClearGloss() {
        return (!word.glossComputed || word.pos !== null) &&
            word.linksFrom.find(l => l.typeId === '>' || l.typeId == '=')
    }

    function clearGloss() {
        callApiAndRefresh(
            () => updateWord(graph, word.id, word.text, null, null, null, null, word.reconstructed, word.sourceEditableText, null),
            router, setErrorText
        )
    }

    if (word === undefined) {
        return <div>No such word in the dictionary</div>
    }

    const isName = word.pos === "NP"
    const isCompound = word.compound
    const classesEditable = word.classes.join(" ")

    const [dictionaryTitle, dictionaryLink] =
        isName ? ["Names", "/names"] :
            (isCompound ? ["Compounds", "/compounds"] : ["Dictionary", ""])

    const componentsOfDerivational = word.components.filter(c => c.derivation)
    const componentsOfNonDerivational = word.components.filter(c => !c.derivation)

    const dictionaries = globalState.languages.find((c) => c.shortName === word.language)?.dictionaries

    return <>
        <Breadcrumbs langId={word.language} langName={word.languageFullName}
                     steps={[{title: dictionaryTitle, url: `/${graph}/dictionary/${word.language}${dictionaryLink}`}]}>
            <WordWithStress text={word.text} stressIndex={word.stressIndex} stressLength={word.stressLength}
                            reconstructed={word.reconstructed || word.languageReconstructed}/>
        </Breadcrumbs>

        {!editMode && <>
            {word.pos && <div>{word.pos} {word.classes.length > 0 && "(" + word.classes.join(", ") + ")"}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? <WordFullGloss word={word}/> : <WordGloss gloss={word.gloss}/>}
                {canClearGloss() && <>{' '}<button className="inlineButton" onClick={() => clearGloss()}>clear</button></>}
            </p>
            {word.notes && <p>{word.notes}</p>}
            <SourceRefs source={word.source}/>

            {allowEdit() && dictionaries.includes("wiktionary") && <p>
                <button className="inlineButton" onClick={() => lookupWordClicked()}>Look up in Wiktionary</button><br/>
                {lookupErrorText !== "" && <span className="errorText">{lookupErrorText}</span>}
                {lookupVariants.length > 0 && <ul>
                    {lookupVariants.map(variant => (
                        <li><button className="inlineButton inlineButtonNormal"
                                    onClick={() => lookupWordClicked(variant.disambiguation)}>
                            {variant.text}
                        </button></li>)
                    )}
                </ul>}
            </p>}

            {allowEdit() && <>
                <button className="inlineButton" onClick={() => suggestParseCandidatesClicked()}>Suggest parse candidates</button>
                <br/>
                {parseCandidates.map(pc => (
                    <>
                        <p>
                            {pc.wordId !== null &&
                                <Link href={`/${graph}/word/${word.language}/${pc.text}/${pc.wordId}`}>{pc.text}</Link>}
                            {pc.wordId === null && <i>{pc.text}</i>}
                            {pc.categories.length === 0 && ` (${pc.ruleNames.join(",")})`}
                            {pc.categories}?{' '}
                            <button onClick={() => acceptParseCandidate(pc)}>Accept</button>
                        </p>
                    </>))}</>}
        </>}
        {editMode && <WordForm
            updateId={word.id}
            defaultValues={{
                language: word.language,
                text: word.textWithExplicitStress,
                gloss: word.glossComputed ? undefined : word.gloss,
                fullGloss: word.fullGloss,
                pos: word.pos,
                classes: classesEditable,
                reconstructed: word.reconstructed,
                source: word.sourceEditableText,
                notes: word.notes
            }}
            languageReadOnly={true}
            wordSubmitted={editSubmitted}
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

        <WordLinkTypeComponent word={word} links={word.linksFrom} directionFrom={true}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} directionFrom={false}/>

        {word.compounds.length > 0 &&
            <>
                <div>Component of compounds</div>
                {word.compounds.map(m => <div><CompoundRefComponent key={m.id} baseWord={word} linkWord={m}/></div>)}
                <p/>
            </>
        }
        {word.derivationalCompounds.length > 0 &&
            <>
                <div>{word.pos === "PV" ? "Affix in:" : "Words derived with affix:"}</div>
                {word.derivationalCompounds.map(m => <div><CompoundRefComponent key={m.id} baseWord={word} linkWord={m}/></div>)}
                <p/>
            </>
        }
        {componentsOfDerivational.length > 0 && <CompoundListComponent word={word} compounds={componentsOfDerivational} derivation={true}/>}
        {componentsOfNonDerivational.length > 0 && <CompoundListComponent word={word} compounds={componentsOfNonDerivational} derivation={false}/>}

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
            {!isCompound && <><button onClick={() => setShowBaseWord(!showBaseWord)}>Add lemma</button><br/></>}
            {showBaseWord && <WordForm wordSubmitted={submitted} linkType='>' linkTarget={word} reverseLink={true}
                                       defaultValues={{language: word.language}} cancelled={() => setShowBaseWord(false)}/>}
            <button onClick={() => setShowDerivedWord(!showDerivedWord)}>Add inflected form</button><br/>
            {showDerivedWord && <WordForm wordSubmitted={submitted} linkType='>' linkTarget={word}
                                          defaultValues={{language: word.language}} cancelled={() => setShowDerivedWord(false)} />}
            <button onClick={() => setShowOriginWord(!showOriginWord)}>Add origin word</button><br/>
            {showOriginWord && <WordForm wordSubmitted={submitted} linkType='^' linkTarget={word} reverseLink={true}
                                         defaultValues={{gloss: word.gloss}} cancelled={() => setShowOriginWord(false)}/>}
            <button onClick={() => setShowDerivativeWord(!showDerivativeWord)}>Add derivative word</button>
            {showDerivativeWord && <WordForm wordSubmitted={submitted} linkType='^' linkTarget={word}
                                             defaultValues={{gloss: word.gloss}} cancelled={() => setShowDerivativeWord(false)}/>}
            {word.suggestedDeriveSequences.map(seq => <>
                {' '}
                <button className="inlineButton" onClick={() => deriveThroughSequenceClicked(seq.id)}>Derive through {seq.name}</button>
            </>)}
            <br/>
            <button onClick={defineAsCompoundClicked}>Define as compound</button><br/>
            {showCompoundComponent && <>
                {compoundSuggestions.map(c => <>
                    <button className="inlineButton" onClick={() => acceptCompoundSuggestion(c.id)}>
                        {c.text}{c.homonym && " '" + c.gloss + "'"}
                    </button>{' '}
                </>)}
                {compoundSuggestions.length > 0 && <br/>}
                <WordForm wordSubmitted={submitted} newCompound={true} linkTarget={word}
                          defaultValues={{language: word.language}} cancelled={() => setShowCompoundComponent(false)}/>
                </>
            }
            <button onClick={() => setShowRelated(!showRelated)}>Add related word</button><br/>
            {showRelated && <WordForm wordSubmitted={submitted} linkType='~' linkTarget={word} defaultValues={{language: word.language}} languageReadOnly={true} cancelled={() => setShowRelated(false)}/>}
            {!isCompound && <><button onClick={() => setShowVariationOf(!showVariationOf)}>Add variation of</button><br/></>}
            {showVariationOf && <WordForm wordSubmitted={submitted} linkType='=' reverseLink={true} linkTarget={word} defaultValues={{language: word.language}} languageReadOnly={true} cancelled={() => setShowVariationOf(false)}/>}
            {!isCompound && <><button onClick={() => setShowVariation(!showVariation)}>Add variation</button><br/></>}
            {showVariation && <WordForm wordSubmitted={submitted} linkType='=' linkTarget={word} defaultValues={{language: word.language}} languageReadOnly={true} cancelled={() => setShowVariation(false)}/>}
            <button onClick={() => setShowRuleLink(!showRuleLink)}>Add related rule</button><br/>
            {showRuleLink && <RuleLinkForm submitted={ruleLinkSubmitted} fromEntityId={word.id}/>}
            <p/>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </>}
        {word.hasParadigms && <Link href={`/${graph}/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}
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
