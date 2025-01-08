import {useState} from "react";
import WordForm, {WordFormData} from "@/forms/WordForm";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faEdit, faCheck} from '@fortawesome/free-solid-svg-icons'
import WordWithStress from "@/components/WordWithStress";
import {
    fetchBackend,
    associateWord,
    allowEdit,
    fetchAlternatives,
    acceptAlternative,
    fetchPathsForAllGraphs, callApiAndRefresh, lockWordAssociations
} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import SourceRefs from "@/components/SourceRefs";
import CorpusTextForm from "@/forms/CorpusTextForm";
import TranslationForm from "@/forms/TranslationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import WordGloss from "@/components/WordGloss";
import {CorpusTextViewModel, CorpusWordCandidateViewModel, CorpusWordViewModel, WordViewModel} from "@/models";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `corpus/text/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("corpus", (corpusText => ({id: corpusText.id.toString()})))
}

interface CorpusTextWordLinkProps {
    word: CorpusWordViewModel
    corpusText: CorpusTextViewModel
    showWordForm: (text: string, index: number) => void
}

export function CorpusTextWordLink(params: CorpusTextWordLinkProps) {
    const w = params.word
    const corpusText = params.corpusText
    const showWordForm = params.showWordForm
    const [hovered, setHovered] = useState(false)
    const router = useRouter()

    if (w.wordCandidates && w.wordCandidates.length > 1) {
        return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
            {w.text}
            {hovered && allowEdit() && <span className="iconWithMargin"><FontAwesomeIcon icon={faEdit}
                                                                                         onClick={() => showWordForm(w.normalizedText, w.index)}/></span>}
        </span>
    } else if (w.wordText || w.gloss) {
        let linkText = (w.wordText ?? w.normalizedText).toLowerCase()
        if (w.wordId !== null && w.homonym) {
            linkText += `/${w.wordId}`
        }
        return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
            <Link href={`/${router.query.graph}/word/${corpusText.language}/${linkText}`}>
                <WordWithStress text={w.text} stressIndex={w.stressIndex} stressLength={w.stressLength}/>
            </Link>
            {hovered && allowEdit() && <span className="iconWithMargin"><FontAwesomeIcon icon={faEdit}
                                                                                         onClick={() => showWordForm(w.normalizedText, w.index)}/></span>}
        </span>
    } else {
        return <span className="undefWord" onClick={() => {
            if (allowEdit()) showWordForm(w.normalizedText, w.index)
        }}>{w.text}</span>
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
    const [hovered, setHovered] = useState(false)

    async function acceptGloss() {
        await acceptAlternative(graph, params.corpusText.id, params.word.index, c.id, -1)
        router.replace(router.asPath)
    }

    return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
        <Link href={`/${graph}/word/${lang}/${params.word.text.toLowerCase()}/${c.id}`}>
            <WordGloss gloss={c.gloss}/>
        </Link>
        {hovered && allowEdit() &&
            <span className="iconWithMargin"><FontAwesomeIcon icon={faCheck} onClick={() => acceptGloss()}/></span>}
    </span>
}

export default function CorpusText(params) {
    const corpusText = params.loaderData as CorpusTextViewModel
    const [editMode, setEditMode] = useState(false)
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [predefWord, setPredefWord] = useState("")
    const [wordIndex, setWordIndex] = useState(-1)
    const [showTranslationForm, setShowTranslationForm] = useState(false)
    const [editTranslationId, setEditTranslationId] = useState(undefined)
    const [alternatives, setAlternatives] = useState([])
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph as string;

    function textSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function wordSubmitted(word: WordViewModel, baseWord: WordViewModel, data: WordFormData) {
        setWordFormVisible(false)
        associateWord(graph, Number.parseInt(router.query.id as string), word.id, wordIndex, data.contextGloss).then(() => {
            const targetWord = baseWord !== undefined ? baseWord : word
            if (targetWord.gloss === "" || targetWord.gloss === null) {
                router.push(`/${graph}/word/${targetWord.language}/${targetWord.text}`)
            } else {
                router.replace(router.asPath)
            }
        })
    }

    async function showWordForm(text, index) {
        const r = await fetchAlternatives(graph, corpusText.id, index)
        setAlternatives(r)
        setWordFormVisible(true)
        setPredefWord(text)
        setWordIndex(index)
    }

    async function acceptAlternativeClicked(index, wordId, ruleId) {
        await acceptAlternative(graph, corpusText.id, index, wordId, ruleId)
        router.replace(router.asPath)
        setWordFormVisible(false)
    }

    function toggleTranslationForm(id?: number) {
        if (showTranslationForm && editTranslationId === id) {
            setShowTranslationForm(false)
        } else {
            setShowTranslationForm(true)
            setEditTranslationId(id)
        }
    }

    function translationSubmitted() {
        setShowTranslationForm(false)
        setEditTranslationId(undefined)
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
            {corpusText.lines.map(l => (
                <div key={l.words[0].index}>
                    <div>
                        {l.words.map((w, index) =>
                            <span className="corpusTextWord">
                                <CorpusTextWordLink word={w} corpusText={corpusText} showWordForm={showWordForm}/><br/>
                                {w.wordCandidates && w.wordCandidates.length > 1 &&
                                    w.wordCandidates.map((c, i) => <>
                                        {i > 0 && " | "}
                                        <CorpusTextGlossChoice corpusText={corpusText} word={w} candidate={c}/>
                                    </>)}
                                {(!w.wordCandidates || w.wordCandidates.length <= 1) && <WordGloss gloss={w.gloss}/>}
                                {w.contextGloss && <><br/><span className="contextGloss">{w.contextGloss}</span></>}
                            </span>)}
                    </div>
                    {wordIndex >= l.words[0].index && wordIndex <= l.words[l.words.length - 1].index && wordFormVisible &&
                        <>
                            <div>{alternatives.map(alt => <>
                                <button className="inlineButton"
                                        onClick={() => acceptAlternativeClicked(wordIndex, alt.wordId, alt.ruleId)}>
                                    {alt.gloss + '?'}
                                </button>
                                {' '}
                            </>)}</div>
                            <WordForm key={predefWord} wordSubmitted={wordSubmitted}
                                      defaultValues={{
                                          language: corpusText.language,
                                          text: predefWord,
                                          contextGloss: l.words[wordIndex - l.words[0].index].contextGloss
                                      }}
                                      languageReadOnly={true}
                                      linkType=">"
                                      reverseLink={true}
                                      linkTargetText={predefWord}
                                      showContextGloss={true}
                                      cancelled={() => setWordFormVisible(false)}/>
                        </>
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
        {corpusText.translations.length > 0 && <>
            <h3>Translations</h3>
            {corpusText.translations.map(t => <>
                {(!showTranslationForm || editTranslationId !== t.id) && <>
                    <div>{t.text} <SourceRefs source={t.source}/></div>
                    {allowEdit() && <button onClick={() => toggleTranslationForm(t.id)}>Edit translation</button>}
                </>}
                {showTranslationForm && editTranslationId === t.id &&
                    <TranslationForm corpusTextId={router.query.id}
                                     updateId={t.id}
                                     defaultValues={{
                                         text: t.text,
                                         source: t.sourceEditableText
                                     }}
                                     submitted={translationSubmitted}
                                     cancelled={() => toggleTranslationForm(t.id)}/>}
            </>)}
        </>}
        {allowEdit() && <p>
            {!editMode && <>
                <button onClick={() => setEditMode(true)}>Edit</button>
                {' '}</>}
            <button onClick={() => toggleTranslationForm(undefined)}>Add translation</button>
            {' '}
            <button onClick={() => lockAssociationsClicked()}>Lock associations</button>
        </p>}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {showTranslationForm && editTranslationId === undefined &&
            <TranslationForm
                corpusTextId={router.query.id}
                submitted={translationSubmitted}
                cancelled={() => toggleTranslationForm(undefined)}
            />}
    </>
}
