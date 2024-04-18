import {useState} from "react";
import WordForm from "@/forms/WordForm";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit } from '@fortawesome/free-solid-svg-icons'
import WordWithStress from "@/components/WordWithStress";
import {
    fetchBackend,
    associateWord,
    allowEdit,
    fetchAlternatives,
    acceptAlternative,
    fetchPathsForAllGraphs
} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import SourceRefs from "@/components/SourceRefs";
import CorpusTextForm from "@/forms/CorpusTextForm";
import TranslationForm from "@/forms/TranslationForm";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `corpus/text/${context.params.id}`)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("corpus", (corpusText => ({id: corpusText.id.toString()})))
}

export function CorpusTextWordLink(params) {
    const w = params.word
    const corpusText = params.corpusText
    const showWordForm = params.showWordForm
    const [hovered, setHovered] = useState(false)
    const router = useRouter()

    if (w.wordText || w.gloss) {
        let linkText = (w.wordText ?? w.normalizedText).toLowerCase()
        if (w.wordId !== null && w.homonym) {
            linkText += `/${w.wordId}`
        }
        return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
            <Link href={`/${router.query.graph}/word/${corpusText.language}/${linkText}`}>
                <WordWithStress text={w.text} stressIndex={w.stressIndex} stressLength={w.stressLength}/>
            </Link>
            {hovered && allowEdit() && <span className="iconWithMargin"><FontAwesomeIcon icon={faEdit} onClick={() => showWordForm(w.normalizedText, w.index)}/></span>}
        </span>
    }
    else {
        return <span className="undefWord" onClick={() => {
            if (allowEdit()) showWordForm(w.normalizedText, w.index)
        }}>{w.text}</span>
    }
}

export default function CorpusText(params) {
    const corpusText = params.loaderData
    const [editMode, setEditMode] = useState(false)
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [predefWord, setPredefWord] = useState("")
    const [wordIndex, setWordIndex] = useState(-1)
    const [showTranslationForm, setShowTranslationForm] = useState(false)
    const [editTranslationId, setEditTranslationId] = useState(undefined)
    const [alternatives, setAlternatives] = useState([])
    const router = useRouter()
    const graph = router.query.graph

    function textSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function wordSubmitted(word) {
        setWordFormVisible(false)
        associateWord(graph, router.query.id, word.id, wordIndex).then(() => {
            if (word.gloss === "" || word.gloss === null) {
                router.push(`/${graph}/word/${word.language}/${word.text}`)
            }
            else {
                router.replace(router.asPath)
            }
        })
    }

    function showWordForm(text, index) {
        fetchAlternatives(graph, corpusText.id, index)
            .then(r => {
                setAlternatives(r)
                setWordFormVisible(true)
                setPredefWord(text)
                setWordIndex(index)
            })
    }

    function acceptAlternativeClicked(index, wordId, ruleId) {
        acceptAlternative(graph, corpusText.id, index, wordId, ruleId)
            .then(r => {
                router.replace(router.asPath)
                setWordFormVisible(false)
            })
    }

    function toggleTranslationForm(id) {
        if (showTranslationForm && editTranslationId === id) {
            setShowTranslationForm(false)
        }
        else {
            setShowTranslationForm(true)
            setEditTranslationId(id)
        }
    }

    function translationSubmitted() {
        setShowTranslationForm(false)
        setEditTranslationId(undefined)
        router.replace(router.asPath)
    }

    return <>
        <Breadcrumbs langId={corpusText.language} langName={corpusText.languageFullName} title={corpusText.title}
                     steps={[
                         {title: "Corpus", url: `/${graph}/corpus/${corpusText.language}`}
                     ]}/>
        {!editMode && <>
            {corpusText.lines.map(l => (
                <div key={l.words[0].index}>
                    <table><tbody>
                        <tr>
                            {l.words.map(w => <td key={w.index}>
                                <CorpusTextWordLink word={w} corpusText={corpusText} showWordForm={showWordForm}/>
                            </td>)}
                        </tr>
                        <tr>
                            {l.words.map(w => <td key={w.index}>{w.gloss}</td>)}
                        </tr>
                    </tbody></table>
                    {wordIndex >= l.words[0].index && wordIndex <= l.words[l.words.length - 1].index && wordFormVisible &&
                        <>
                            <div>{alternatives.map(alt => <>
                                <button className="inlineButton"
                                        onClick={() => acceptAlternativeClicked(wordIndex, alt.wordId, alt.ruleId)}>
                                    {alt.gloss + '?'}
                                </button>
                                {' '}
                            </>)}</div>
                            <WordForm key={predefWord} submitted={wordSubmitted}
                                      defaultValues={{
                                          language: corpusText.language,
                                          text: predefWord,
                                          source: corpusText.sourceEditableText
                                      }}
                                      languageReadOnly={true}
                                      textReadOnly={true}/>
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
        {editMode && <CorpusTextForm lang={corpusText.lang}
                                     updateId={corpusText.id}
                                     defaultValues={{
                                         title: corpusText.title,
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
            {!editMode && <><button onClick={() => setEditMode(true)}>Edit</button>{' '}</>}
            <button onClick={() => toggleTranslationForm(undefined)}>Add translation</button>
        </p>}
        {showTranslationForm && editTranslationId === undefined &&
            <TranslationForm
                corpusTextId={router.query.id}
                submitted={translationSubmitted}
                cancelled={() => toggleTranslationForm(undefined)}
            />}
    </>
}