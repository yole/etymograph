import {useState} from "react";
import WordForm, {WordFormData} from "@/forms/WordForm";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faEdit, faCheck, faCommentDots, faEraser} from '@fortawesome/free-solid-svg-icons'
import {
    fetchBackend,
    associateWord,
    fetchAlternatives,
    acceptAlternative,
    fetchPathsForAllGraphs, callApiAndRefresh, lockWordAssociations, deleteTranslation, useAllowEditGraph
} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import SourceRefs from "@/components/SourceRefs";
import CorpusTextForm from "@/forms/CorpusTextForm";
import TranslationForm from "@/forms/TranslationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {LinkTypes} from "@/components/LinkTypes";
import WordGloss from "@/components/WordGloss";
import {CorpusLineViewModel, CorpusTextViewModel, CorpusWordCandidateViewModel, CorpusWordViewModel, TranslationViewModel, WordViewModel} from "@/models";
import WordTextView from "@/components/WordTextView";
import {Urls} from "@/components/Urls";
import {ActionIcon, Popover} from "@mantine/core";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `corpus/text/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("corpus", (corpusText => ({id: corpusText.id.toString()})))
}

interface CorpusTextWordLinkProps {
    word: CorpusWordViewModel
    corpusText: CorpusTextViewModel
}

export function CorpusTextWordLink(params: CorpusTextWordLinkProps) {
    const w = params.word
    const corpusText = params.corpusText
    const router = useRouter()

    if (w.wordCandidates && w.wordCandidates.length > 1) {
        return <WordTextView text={w.text} syllabograms={w.syllabogramSequence}/>
    }
    else if (!w.glossable) {
        return <>{w.text}</>
    }
    else if (w.wordText || w.gloss) {
        let linkText = (w.wordUrlKey ?? w.wordText ?? w.normalizedText).toLowerCase()
        if (w.wordId !== null && (w.homonym || w.wordUrlKey)) {
            linkText += `/${w.wordId}`
        }
        return <span>
            <Link href={`/${router.query.graph}/word/${corpusText.language}/${linkText}`}>
                <WordTextView text={w.text} syllabograms={w.syllabogramSequence}
                              stressIndex={w.stressIndex} stressLength={w.stressLength}/>
            </Link>
        </span>
    }
    else {
        return <WordTextView text={w.text} syllabograms={w.syllabogramSequence}/>
    }
}

interface CorpusTextGlossChoiceProps {
    corpusText: CorpusTextViewModel,
    word: CorpusWordViewModel,
    candidate?: CorpusWordCandidateViewModel
}

function CorpusTextGlossChoice(params: CorpusTextGlossChoiceProps) {
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = params.corpusText.language
    const c = params.candidate
    const editable = useAllowEditGraph()

    async function acceptGloss() {
        await acceptAlternative(graph, params.corpusText.id, params.word.index, c.id, -1)
        router.replace(router.asPath)
    }

    return <span>
        {editable &&
            <span className="iconWithMarginRight"><FontAwesomeIcon icon={faCheck} onClick={() => acceptGloss()}/></span>}
        <Link href={`/${graph}/word/${lang}/${params.word.text.toLowerCase()}/${c.id}`}>
            <WordGloss gloss={c.gloss}/>
        </Link>
    </span>
}

function GlossDropdown(params: {
    corpusText: CorpusTextViewModel,
    word: CorpusWordViewModel,
    showDefineWordForm: (text: string, index: number) => void,
    showDefineLemmaForm: (text: string, index: number) => void,
    showTranslationFormAtWord: (index: number) => void
}) {
    const [opened, setOpened] = useState(false)
    const editable = useAllowEditGraph()
    let w = params.word
    const candidates = w.wordCandidates
    const showPlus = w.wordCandidates && w.wordCandidates.length > 1

    if (!w.glossable) {
        return ""
    }

    if (!editable) {
        return <WordGloss gloss={w.gloss}/>;
    }

    return <Popover opened={opened} onChange={setOpened} position="bottom-start" withArrow shadow="md">
        <Popover.Target>
            <span className="glossChoice" style={{cursor: "pointer"}} onClick={() => setOpened(o => !o)}>
                {showPlus && <><WordGloss gloss={candidates[0].gloss}/> &lt;+&gt;</>}
                {!showPlus && <WordGloss gloss={w.gloss}/>}
            </span>
        </Popover.Target>
        <Popover.Dropdown>
            {candidates && candidates.filter( c => c.gloss !== null).map(c =>
                <div key={c.id}>
                    <CorpusTextGlossChoice corpusText={params.corpusText} word={w} candidate={c}/>
                </div>)}
            <div><span className="inlineButton" onClick={() => {
                setOpened(false)
                params.showDefineWordForm(w.normalizedText, w.index)
            }}>Define word</span></div>
            <div><span className="inlineButton" onClick={() => {
                setOpened(false)
                params.showDefineLemmaForm(w.normalizedText, w.index)
            }}>Define lemma</span></div>
            <div><span className="inlineButton" onClick={() => {
                setOpened(false)
                params.showTranslationFormAtWord(w.index)
            }}>Translate from here</span></div>
        </Popover.Dropdown>
    </Popover>;
}

export interface TextSegment {
    start: number
    end: number
    translations: TranslationViewModel[]
}

function segmentKey(start: number, end: number): string {
    return `${start}:${end}`
}

// Returns, for each line, the word index at which it sits: for a normal line the index of its
// first word, for a separator line the number of words preceding it.
function computeLinePositions(lines: CorpusLineViewModel[]): number[] {
    let nextIndex = 0
    return lines.map(line => {
        if (line.words.length === 0) {
            return nextIndex
        }
        const start = line.words[0].index
        nextIndex = line.words[line.words.length - 1].index + 1
        return start
    })
}

export function buildSegments(corpusText: CorpusTextViewModel): { segments: TextSegment[], unanchoredTranslations: TranslationViewModel[] } {
    const wordCount = corpusText.lines.flatMap(line => line.words).length
    const boundaries = new Set<number>([0, wordCount])
    const translationsBySegment = new Map<string, TranslationViewModel[]>()
    const unanchoredTranslations: TranslationViewModel[] = []

    corpusText.translations.forEach(translation => {
        const start = translation.anchorStartIndex
        const end = translation.anchorEndIndex
        if (start === null || start === undefined || end === null || end === undefined || start < 0 || end > wordCount || start >= end) {
            unanchoredTranslations.push(translation)
            return
        }

        boundaries.add(start)
        boundaries.add(end)
        const key = segmentKey(start, end)
        const list = translationsBySegment.get(key)
        if (list) {
            list.push(translation)
        } else {
            translationsBySegment.set(key, [translation])
        }
    })

    const sortedBoundaries = Array.from(boundaries).sort((a, b) => a - b)
    const segments: TextSegment[] = []
    for (let i = 0; i < sortedBoundaries.length - 1; i++) {
        const start = sortedBoundaries[i]
        const end = sortedBoundaries[i + 1]
        if (start === end) continue

        segments.push({
            start,
            end,
            translations: translationsBySegment.get(segmentKey(start, end)) ?? []
        })
    }

    return {segments, unanchoredTranslations}
}

function findPreviousTranslationSource(segments: TextSegment[], anchorStart: number): string | undefined {
    let source: string | undefined = undefined
    for (const segment of segments) {
        if (segment.start >= anchorStart) break
        if (segment.translations.length > 0) {
            source = segment.translations[segment.translations.length - 1].sourceEditableText
        }
    }
    return source
}

function TranslationView(params: {translation: TranslationViewModel}) {
    const router = useRouter()
    const graph = router.query.graph as string;
    const t = params.translation
    const [showTranslationForm, setShowTranslationForm] = useState(false)
    const editable = useAllowEditGraph()

    function translationSubmitted() {
        setShowTranslationForm(false)
        router.replace(router.asPath)
    }

    function deleteTranslationClicked(id: number) {
        if (window.confirm("Delete this translation?")) {
            deleteTranslation(graph, id).then(() => router.replace(router.asPath))
        }
    }

    return <>
        {(!showTranslationForm) && <>
            <div className="translation">
                &quot;{t.text}&quot; <SourceRefs source={t.source} span={true}/>
                {editable && <>
                    <ActionIcon className="iconWithMargin" type="button" variant="outline" onClick={() => setShowTranslationForm(!showTranslationForm)}>
                        <FontAwesomeIcon icon={faEdit}/>
                    </ActionIcon>
                    <ActionIcon type="button" variant="outline" onClick={() => deleteTranslationClicked(t.id)}>
                        <FontAwesomeIcon icon={faEraser}/>
                    </ActionIcon>
                </>}
            </div>
        </>}
        {showTranslationForm &&
            <TranslationForm corpusTextId={Number.parseInt(router.query.id as string)}
                             updateId={t.id}
                             defaultValues={{
                                 text: t.text,
                                 source: t.sourceEditableText
                             }}
                             submitted={translationSubmitted}
                             cancelled={() => setShowTranslationForm(!showTranslationForm)}
                             focusTarget='text'
            />}
    </>
}

export default function CorpusText(params) {
    const corpusText = params.loaderData as CorpusTextViewModel
    const [editMode, setEditMode] = useState(false)
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [wordFormMode, setWordFormMode] = useState<'word' | 'lemma'>('word')
    const [predefWord, setPredefWord] = useState("")
    const [wordIndex, setWordIndex] = useState(-1)
    const [showTranslationForm, setShowTranslationForm] = useState(false)
    const [newTranslationAnchorStart, setNewTranslationAnchorStart] = useState<number | undefined>(undefined)
    const [alternatives, setAlternatives] = useState([])
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph as string;
    const allWords = corpusText.lines.flatMap(l => l.words)
    const {segments, unanchoredTranslations} = buildSegments(corpusText)
    const linePositions = computeLinePositions(corpusText.lines)
    const editable = useAllowEditGraph()

    function textSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function wordSubmitted(word: WordViewModel, baseWord: WordViewModel, data: WordFormData) {
        setWordFormVisible(false)
        associateWord(graph, Number.parseInt(router.query.id as string), word.id, wordIndex, data.contextGloss).then(() => {
            const targetWord = baseWord !== undefined ? baseWord : word
            if (targetWord.gloss === "" || targetWord.gloss === null) {
                router.push(Urls.Words.fromWordForm(graph, targetWord))
            } else {
                router.replace(router.asPath)
            }
        })
    }

    async function showWordForm(text: string, index: number, mode: 'word' | 'lemma') {
        if (mode === 'word') {
            const r = await fetchAlternatives(graph, corpusText.id, index)
            setAlternatives(r)
        } else {
            setAlternatives([])
        }
        setWordFormMode(mode)
        setWordFormVisible(true)
        setPredefWord(text)
        setWordIndex(index)
    }

    async function acceptAlternativeClicked(index: number, wordId: number, ruleId: number) {
        await acceptAlternative(graph, corpusText.id, index, wordId, ruleId)
        router.replace(router.asPath)
        setWordFormVisible(false)
    }

    function toggleTranslationForm(anchorStartIndex?: number) {
        if (showTranslationForm && newTranslationAnchorStart === anchorStartIndex) {
            setShowTranslationForm(false)
            setNewTranslationAnchorStart(undefined)
        } else {
            setShowTranslationForm(true)
            setNewTranslationAnchorStart(anchorStartIndex)
        }
    }

    function translationSubmitted() {
        setShowTranslationForm(false)
        setNewTranslationAnchorStart(undefined)
        router.replace(router.asPath)
    }

    function lockAssociationsClicked() {
        callApiAndRefresh(() => lockWordAssociations(graph, corpusText.id),
            router, setErrorText)
    }

    return <>
        <Breadcrumbs langId={corpusText.language} langName={corpusText.languageFullName} title={corpusText.title}
                     steps={[
                         {title: "Corpus", url: `/${graph}/corpus/${corpusText.language}`}
                     ]}/>
        {!editMode && <>
            {segments.map(segment => (
                <div key={`segment-${segment.start}-${segment.end}`} className="segment">
                    {corpusText.lines.map((line, lineIdx) => {
                        if (line.separator) {
                            // A separator within a segment is rendered inline, between the segment's lines.
                            // Separators sitting on a segment boundary are rendered after the segment instead.
                            const p = linePositions[lineIdx]
                            if ((p > segment.start && p < segment.end) || (p === 0 && segment.start === 0)) {
                                return <hr key={`separator-${lineIdx}`} className="corpusTextSeparator"/>
                            }
                            return null
                        }
                        const segmentWords = line.words.filter(w => w.index >= segment.start && w.index < segment.end)
                        if (segmentWords.length === 0) return null

                        return <div key={`line-${segment.start}-${segment.end}-${line.words[0].index}`}>
                            <div className="corpusTextLine">
                                {line.lineNumber && <span className="corpusTextLineNumber">{line.lineNumber} </span>}
                                {segmentWords.map((w, index) =>
                                    <span className="corpusTextWord" key={index}>
                                        <CorpusTextWordLink word={w} corpusText={corpusText}/>
                                        <br/>
                                        <GlossDropdown
                                            corpusText={corpusText}
                                            word={w}
                                            showDefineWordForm={(text, index) => showWordForm(text, index, 'word')}
                                            showDefineLemmaForm={(text, index) => showWordForm(text, index, 'lemma')}
                                            showTranslationFormAtWord={toggleTranslationForm}/>
                                        {w.contextGloss && <><br/><span className="contextGloss">{w.contextGloss}</span></>}
                                    </span>)}
                            </div>
                            {wordIndex >= line.words[0].index && wordIndex <= line.words[line.words.length-1].index &&
                                wordIndex >= segment.start && wordIndex < segment.end && wordFormVisible &&
                                <>
                                    <div>{alternatives.map(alt => <>
                                        <button className="inlineButton"
                                                onClick={() => acceptAlternativeClicked(wordIndex, alt.wordId, alt.ruleId)}>
                                            {alt.gloss + '?'}
                                        </button>
                                        {' '}
                                    </>)}</div>
                                    <WordForm key={`${wordFormMode}:${predefWord}`} wordSubmitted={wordSubmitted}
                                              defaultValues={{
                                                  language: corpusText.language,
                                                  text: predefWord,
                                                  contextGloss: allWords.find(w => w.index === wordIndex)?.contextGloss,
                                                  syllabographic: corpusText.syllabographic,
                                              }}
                                              languageReadOnly={true}
                                              textReadOnly={wordFormMode === 'word'}
                                              linkType={wordFormMode === 'lemma' ? LinkTypes.Derived : undefined}
                                              reverseLink={wordFormMode === 'lemma'}
                                              linkTargetText={wordFormMode === 'lemma' ? predefWord : undefined}
                                              forceNewLinkTarget={wordFormMode === 'lemma'}
                                              showContextGloss={true}
                                              showSyllabographic={true}
                                              cancelled={() => setWordFormVisible(false)}/>
                                </>
                            }
                        </div>
                    })}
                    {segment.translations.length > 0 && <>
                        {segment.translations.length > 1 && <h4>Translations</h4>}
                        {segment.translations.map(t =>
                            <TranslationView translation={t}></TranslationView>
                        )}
                    </>}
                    {corpusText.lines.map((line, lineIdx) =>
                        line.separator && linePositions[lineIdx] === segment.end
                            ? <hr key={`separator-${lineIdx}`} className="corpusTextSeparator"/>
                            : null
                    )}
                    {showTranslationForm && newTranslationAnchorStart !== undefined &&
                        newTranslationAnchorStart >= segment.start && newTranslationAnchorStart < segment.end &&
                        <TranslationForm
                            corpusTextId={Number.parseInt(router.query.id as string)}
                            anchorStartIndex={newTranslationAnchorStart}
                            defaultValues={{text: "", source: findPreviousTranslationSource(segments, newTranslationAnchorStart)}}
                            submitted={translationSubmitted}
                            cancelled={() => {
                                setShowTranslationForm(false)
                                setNewTranslationAnchorStart(undefined)
                            }}
                            focusTarget='text'
                        />
                    }
                </div>
            ))}
            <SourceRefs source={corpusText.source}/>
        </>}
        {corpusText.notes != null && <>
            <h3>Notes</h3>
            <p>{corpusText.notes}</p>
        </>}
        {editMode && <CorpusTextForm lang={corpusText.language}
                                     updateId={corpusText.id}
                                     defaultValues={{
                                         title: corpusText.title === "Untitled" ? "" : corpusText.title,
                                         text: corpusText.text,
                                         source: corpusText.sourceEditableText,
                                         notes: corpusText.notes
                                     }}
                                     submitted={textSubmitted}
                                     cancelled={() => setEditMode(false)}/>}
        {unanchoredTranslations.length > 0 && <>
            <h3>Translations</h3>
            {unanchoredTranslations.map(t => <>
                <TranslationView translation={t}/>
            </>)}
        </>}
        {editable && <p>
            {!editMode && <>
                <button className="uiButton" onClick={() => setEditMode(true)}>Edit</button>
                {' '}
            </>}
            <button className="uiButton" onClick={() => lockAssociationsClicked()}>Lock associations</button>
        </p>}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
