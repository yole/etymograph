import {useContext, useEffect, useState} from "react";
import WordForm from "@/forms/WordForm";
import WordLink from "@/components/WordLink";
import {LinkTypes} from "@/components/LinkTypes";
import {
    addLink,
    addWord,
    deleteLink,
    deleteWord,
    fetchBackend,
    deleteCompound,
    applyRuleSequence,
    deriveThroughRuleSequence,
    fetchAllLanguagePaths,
    lookupWord,
    suggestParseCandidates,
    suggestCompound,
    callApiAndRefresh,
    updateWord,
    refreshLinkSequence, suggestTranscription, allowEditGraph, hasBackend
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
    AttestationViewModel, CompoundComponentsViewModel, CompoundRefViewModel, DictionaryViewModel, LinkTypeViewModel, LinkWordViewModel,
    LookupVariantViewModel,
    ParseCandidateViewModel, WordRefViewModel,
    WordViewModel
} from "@/models";
import WordTextView from "@/components/WordTextView";
import WordPickerModal from "@/components/WordPickerModal";
import {Urls} from "@/components/Urls";
import {Accordion, Alert} from "@mantine/core";

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
        const dictViewModel = dictData.props.loaderData as DictionaryViewModel
        for (const word of dictViewModel.words) {
            paths.push({params: {...path.params, text: [word.ref.urlKey ?? word.ref.text.toLowerCase()]}})
            if (word.ref.homonym) {
                paths.push({params: {...path.params, text: [word.ref.urlKey ?? word.ref.text.toLowerCase(), word.ref.id.toString()]}})
            }
        }
    }
    return {paths, fallback: hasBackend()}
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

function InlineAttestations({attestations, graph}: {attestations: AttestationViewModel[], graph: string}) {
    return <span>
        {' '} in {attestations.map((att, i) => <span key={`${att.textId}-${i}`}>
            {i > 0 && ", "}
            <Link href={`/${graph}/corpus/text/${att.textId}`}>{att.textTitle}</Link>
            {att.word && <>{' ('}<WordTextView text={att.word} syllabograms={att.syllabogramSequence}/>{')'}</>}
        </span>)}
    </span>
}

export function WordLinkComponent(params: WordLinkProps) {
    const baseWord = params.baseWord
    const linkWord = params.linkWord
    const [editMode, setEditMode] = useState(false)
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph as string
    const canEdit = allowEditGraph()

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
        {params.linkType.typeId === LinkTypes.Derived && !params.directionFrom && linkWord.attestations.length > 0 &&
            <InlineAttestations attestations={linkWord.attestations} graph={graph}/>
        }
        {canEdit && <>
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

function CompoundRefComponent(params: {baseWord: WordViewModel, linkWord: CompoundRefViewModel}) {
    const {baseWord, linkWord} = params
    const router = useRouter()
    const graph = router.query.graph as string
    return <>
        <WordLink word={linkWord.word} baseLanguage={baseWord.language} gloss={true}/>
        {linkWord.attestations.length > 0 && <InlineAttestations attestations={linkWord.attestations} graph={graph}/>}
    </>
}

function CompoundListComponent(
    {compounds, word, derivation}: {compounds: CompoundComponentsViewModel[], word: WordViewModel, derivation: boolean}
) {
    const router = useRouter()
    const graph = router.query.graph as string

    const [addToCompoundId, setAddToCompoundId] = useState(undefined)
    const [editCompound, setEditCompound] = useState(undefined)
    const [compoundSuggestions, setCompoundSuggestions] = useState([] as WordRefViewModel[])
    const canEdit = allowEditGraph()

    async function prepareAddToCompound(compoundId: number) {
        setAddToCompoundId(compoundId)
        const r = await suggestCompound(graph, word.id, compoundId)
        if (r.ok()) {
            const jr = await r.result()
            setCompoundSuggestions(jr.suggestions)
        }
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
                {m.components.map((mc, index) => <span key={mc.id}>
                    {index > 0 && " + "}
                    <WordLink word={mc} baseLanguage={word.language} gloss={true}/>
                    {index === m.headIndex && " (head)"}
                </span>)}
                {m.notes && <> &ndash; {m.notes}</>}
                <SourceRefs source={m.source} span={true}/>
                <WordPickerModal opened={addToCompoundId === m.compoundId} onClose={() => setAddToCompoundId(undefined)}
                                 title="Add component"
                                 addToCompound={m.compoundId} linkTarget={word}
                                 suggestions={compoundSuggestions}
                                 showSyllabographic={word.syllabographic}
                                 defaultValues={{language: word.language}} wordSubmitted={submitted}/>
                {editCompound === m.compoundId &&
                    <EditLinkForm compoundId={m.compoundId}
                                  compoundComponents={m.components}
                                  compoundHead={m.headIndex === null || m.headIndex === undefined ? -1 : m.components[m.headIndex].id}
                                  defaultValues={{source: m.sourceEditableText, notes: m.notes}}
                                  submitted={() => {
                                      setEditCompound(undefined)
                                      router.replace(router.asPath)
                                  }}
                                  cancelled={() => setEditCompound(undefined)}/>
                }
                {canEdit && <>
                    {' '}
                    {addToCompoundId !== m.compoundId && <button className="uiButton" onClick={() => prepareAddToCompound(m.compoundId)}>Add component</button>}
                    {' '}
                    {editCompound !== m.compoundId && <button className="uiButton" onClick={() => setEditCompound(m.compoundId)}>Edit compound</button>}
                    {' '}
                    <button className="uiButton" onClick={() => deleteCompoundClicked(m.compoundId)}>Delete</button>
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

function SingleWord({word, embedded}: { word: WordViewModel, embedded?: boolean }) {
    const globalState = useContext(GlobalStateContext)
    const canEdit = allowEditGraph()

    const router = useRouter()
    const graph = router.query.graph as string
    const [showTranscription, setShowTranscription] = useState(false)
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
    const [suggestedTranscription, setSuggestedTranscription] = useState("")
    useEffect(() => { document.title = "Etymograph : " + (word === undefined ? "Unknown Word" : word.text) })

    function submitted() {
        setShowTranscription(false)
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
        if (!embedded && (r.text !== word.text || r.syllabographic !== word.syllabographic)) {
            router.push(Urls.Words.fromWordForm(graph, r))
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
                .then(() => {
                    if (embedded) {
                        router.replace(router.asPath)
                    }
                    else {
                        router.push(`/${graph}/dictionary/${word.language}`);
                    }
                })
        }
    }

    async function suggestParseCandidatesClicked() {
        const r = await suggestParseCandidates(graph, word.id)
        const jr = await r.json()
        setParseCandidates(jr.parseCandidates)
    }

    async function linkToParseCandidate(pc: ParseCandidateViewModel, wordId: number) {
        const r = await addLink(graph, word.id, wordId, LinkTypes.Derived, pc.ruleNames.join(","))
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
            word.linksFrom.find(l =>
                l.typeId === LinkTypes.Derived || l.typeId == LinkTypes.Variation || l.typeId == LinkTypes.Transcription
            )
    }

    function clearGloss() {
        callApiAndRefresh(
            () => updateWord(graph, word.id, word.text, null, null, null, null, word.reconstructed, word.syllabographic, word.sourceEditableText, null),
            router, setErrorText
        )
    }

    async function showTranscriptionClicked() {
        if (!showBaseWord) {
            const r = await suggestTranscription(graph, word.id)
            const result = await r.text()
            setSuggestedTranscription(result)
        }
        setShowTranscription(!showBaseWord)
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

    const canShowTranscription = word.syllabogramSequence !== null
    const canSuggestParseCandidates = !isCompound && !word.syllabogramSequence

    const realGloss = word.glossComputed ? undefined : word.gloss

    return <>
        {!embedded && <Breadcrumbs langId={word.language} langName={word.languageFullName}
                     steps={[{title: dictionaryTitle, url: `/${graph}/dictionary/${word.language}${dictionaryLink}`}]}>
            <WordTextView text={word.text} syllabograms={word.syllabogramSequence}
                    stressIndex={word.stressIndex} stressLength={word.stressLength}
                    reconstructed={word.reconstructed || word.languageReconstructed}/>
        </Breadcrumbs>}
        {embedded && <h3><WordTextView text={word.text} syllabograms={word.syllabogramSequence}
                                       stressIndex={word.stressIndex} stressLength={word.stressLength}
                                       reconstructed={word.reconstructed || word.languageReconstructed}/></h3>}

        {!editMode && <>
            {word.pos && <div>{word.pos} {word.classes.length > 0 && "(" + word.classes.join(", ") + ")"}</div>}
            <p>{word.fullGloss !== null && word.fullGloss !== "" ? <WordFullGloss word={word}/> : <WordGloss gloss={word.gloss}/>}
                {canClearGloss() && <>{' '}<button className="inlineButton" onClick={() => clearGloss()}>clear</button></>}
            </p>
            {word.notes && <p>{word.notes}</p>}
            <SourceRefs source={word.source}/>
            {word.consistencyIssues.map((issue, index) => (
                <Alert key={`${word.id}-${index}`} color="yellow" title="Consistency issue" mb="sm">
                    {issue}
                </Alert>
            ))}
        </>}
        {editMode && <WordForm
            updateId={word.id}
            defaultValues={{
                language: word.language,
                text: word.textWithExplicitStress,
                gloss: realGloss,
                fullGloss: word.fullGloss,
                pos: word.pos,
                classes: classesEditable,
                reconstructed: word.reconstructed,
                syllabographic: word.syllabographic,
                source: word.sourceEditableText,
                notes: word.notes
            }}
            languageReadOnly={true}
            showSyllabographic={word.languageSyllabographic}
            wordSubmitted={editSubmitted}
            cancelled={() => setEditMode(false)}
            />}
        {canEdit && !editMode && <>
            <p/>
            <button className="uiButton" onClick={() => setEditMode(true)}>{"Edit"}</button>&nbsp;
            <button className="uiButton" onClick={() => deleteWordClicked()}>Delete</button>{' '}
            {canEdit && dictionaries.includes("wiktionary") && <>
                <button className="uiButton" onClick={() => lookupWordClicked()}>Look up in Wiktionary</button>
                <p>
                {lookupErrorText !== "" && <span className="errorText">{lookupErrorText}</span>}
                {lookupVariants.length > 0 && <ul>
                    {lookupVariants.map(variant => (
                        <li><button className="inlineButton inlineButtonNormal"
                                    onClick={() => lookupWordClicked(variant.disambiguation)}>
                            {variant.text}
                        </button></li>)
                    )}
                </ul>}
                </p>
            </>}
        </>}

        {!word.baseWord && word.attestations.length > 0 &&
            <>Attested <InlineAttestations attestations={word.attestations} graph={graph}/></>
        }

        <WordLinkTypeComponent word={word} links={word.linksFrom} directionFrom={true}/>
        <WordLinkTypeComponent word={word} links={word.linksTo} directionFrom={false}/>

        {word.compounds.length > 0 &&
            <>
                <div>Component of compounds</div>
                {word.compounds.map(m => <div key={m.word.id}><CompoundRefComponent baseWord={word} linkWord={m}/></div>)}
                <p/>
            </>
        }
        {word.derivationalCompounds.length > 0 &&
            <>
                <div>{word.pos === "PV" ? "Affix in:" : "Words derived with affix:"}</div>
                {word.derivationalCompounds.map(m => <div key={m.word.id}><CompoundRefComponent baseWord={word} linkWord={m}/></div>)}
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
                    {canEdit && rl.canDelete && <>
                        &nbsp;(<span className="inlineButtonLink">
                            <button className="inlineButton" onClick={() => deleteRuleLinkClicked(rl.ruleId, rl.linkType)}>delete</button>
                        </span>)</>
                    }
                    <br/></>
                )}
            </>
        }

        {canEdit && <Accordion defaultValue={word.baseWord || realGloss ? "" : "define"}>
            <Accordion.Item value="define"><Accordion.Control><b>Define this word</b></Accordion.Control><Accordion.Panel>
            {canShowTranscription && <><button className="uiButton" onClick={showTranscriptionClicked}>Transcription</button>{' '}</>}
            <WordPickerModal opened={showTranscription} onClose={() => setShowTranscription(false)} title="Add transcription"
                             linkType={LinkTypes.Transcription} linkTarget={word} reverseLink={true} languageReadOnly={true}
                             defaultValues={{language: word.language, text: suggestedTranscription}}
                             wordSubmitted={submitted}/>

            {!isCompound && <><button className="uiButton" onClick={() => setShowBaseWord(!showBaseWord)}>Lemma</button>{' '}</>}
            <WordPickerModal opened={showBaseWord} onClose={() => setShowBaseWord(false)} title="Add lemma"
                             linkType={LinkTypes.Derived} linkTarget={word} reverseLink={true} languageReadOnly={true}
                             showSyllabographic={word.syllabographic}
                             defaultValues={{language: word.language}} wordSubmitted={submitted}/>

            <button className="uiButton" onClick={defineAsCompoundClicked}>Compound</button>{' '}
            <WordPickerModal opened={showCompoundComponent} onClose={() => setShowCompoundComponent(false)}
                             title="Define compound"
                             newCompound={true} linkTarget={word}
                             suggestions={compoundSuggestions}
                             showSyllabographic={word.syllabographic}
                             defaultValues={{language: word.language}} wordSubmitted={submitted}/>

            {!isCompound && <><button className="uiButton" onClick={() => setShowVariationOf(!showVariationOf)}>Variation</button><br/></>}
            <WordPickerModal opened={showVariationOf} onClose={() => setShowVariationOf(false)} title="Add variation"
                             linkType={LinkTypes.Variation} reverseLink={true} linkTarget={word}
                             defaultValues={{language: word.language}}
                             languageReadOnly={true}
                             showSyllabographic={word.syllabographic}
                             wordSubmitted={submitted}/>

            {canSuggestParseCandidates && <>
                <button className="inlineButton" onClick={() => suggestParseCandidatesClicked()}>Suggest lemma and derivation</button>
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
            </Accordion.Panel></Accordion.Item>
            <Accordion.Item value="link"><Accordion.Control><b>Add linked words</b></Accordion.Control><Accordion.Panel>

            <button className="uiButton" onClick={() => setShowDerivedWord(!showDerivedWord)}>Add inflected form</button>{' '}
            <WordPickerModal opened={showDerivedWord} onClose={() => setShowDerivedWord(false)} title="Add inflected form"
                             linkType={LinkTypes.Derived} linkTarget={word}
                             defaultValues={{language: word.language}} wordSubmitted={submitted}/>

            <button className="uiButton" onClick={() => setShowRelated(!showRelated)}>Add related word</button>{' '}
            <WordPickerModal opened={showRelated} onClose={() => setShowRelated(false)} title="Add related word"
                             linkType={LinkTypes.Related} linkTarget={word}
                             defaultValues={{language: word.language}} languageReadOnly={true} wordSubmitted={submitted}/>


            {!isCompound && <><button className="uiButton" onClick={() => setShowVariation(!showVariation)}>Add variation</button>{' '}</>}
            <WordPickerModal opened={showVariation} onClose={() => setShowVariation(false)} title="Add variation"
                             linkType={LinkTypes.Variation} linkTarget={word}
                             defaultValues={{language: word.language}}
                             languageReadOnly={true}
                             showSyllabographic={word.syllabographic}
                             wordSubmitted={submitted}/>
            <button className="uiButton" onClick={() => setShowRuleLink(!showRuleLink)}>Add related rule</button><br/>
            {showRuleLink && <RuleLinkForm submitted={ruleLinkSubmitted} fromEntityId={word.id}/>}
            </Accordion.Panel></Accordion.Item>
            <Accordion.Item value="etymology"><Accordion.Control><b>Etymology</b></Accordion.Control><Accordion.Panel>

            <button className="uiButton"  onClick={() => setShowOriginWord(!showOriginWord)}>Add origin word</button>{' '}
            <WordPickerModal opened={showOriginWord} onClose={() => setShowOriginWord(false)} title="Add origin word"
                             linkType={LinkTypes.Origin} linkTarget={word} reverseLink={true}
                             defaultValues={{gloss: word.gloss}} wordSubmitted={submitted}/>
            <button className="uiButton"  onClick={() => setShowDerivativeWord(!showDerivativeWord)}>Add derivative word</button>
            <WordPickerModal opened={showDerivativeWord} onClose={() => setShowDerivativeWord(false)} title="Add derivative word"
                             linkType={LinkTypes.Origin} linkTarget={word}
                             defaultValues={{gloss: word.gloss}} wordSubmitted={submitted}/>
            {word.suggestedDeriveSequences.map(seq => <>
                {' '}
                <button className="inlineButton" onClick={() => deriveThroughSequenceClicked(seq.id)}>Derive through {seq.name}</button>
            </>)}
            <br/>
            <p/>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </Accordion.Panel></Accordion.Item></Accordion>}
        {word.hasParadigms && <Link href={`/${graph}/paradigms/${word.language}/word/${word.id}`}>Paradigms</Link>}

        {word.baseWord && <blockquote><SingleWord word={word.baseWord} embedded={true}/></blockquote>}
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
