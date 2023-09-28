import {useEffect, useState} from "react";
import WordForm from "@/components/WordForm";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit } from '@fortawesome/free-solid-svg-icons'
import WordWithStress from "@/components/WordWithStress";
import {fetchBackend, associateWord, allowEdit, addTranslation} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import SourceRefs from "@/components/SourceRefs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`corpus/text/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`corpus`)
    const paths = props.loaderData.corpusTexts.map(corpusText => ({params: {id: corpusText.id.toString()}}))
    return {paths, fallback: allowEdit()}
}

export function CorpusTextWordLink(params) {
    const w = params.word
    const corpusText = params.corpusText
    const showWordForm = params.showWordForm
    const [hovered, setHovered] = useState(false)

    if (w.wordText || w.gloss) {
        let linkText = (w.wordText ?? w.text).toLowerCase()
        if (w.wordId !== null && w.homonym) {
            linkText += `/${w.wordId}`
        }
        return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
            <Link href={`/word/${corpusText.language}/${linkText}`}>
                <WordWithStress text={w.text} stressIndex={w.stressIndex} stressLength={w.stressLength}/>
            </Link>
            {hovered && allowEdit() && <span className="iconWithMargin"><FontAwesomeIcon icon={faEdit} onClick={() => showWordForm(w.text, w.index)}/></span>}
        </span>
    }
    else {
        return <span className="undefWord" onClick={() => {
            if (allowEdit()) showWordForm(w.text, w.index)
        }}>{w.text}</span>
    }
}

export default function CorpusText(params) {
    const corpusText = params.loaderData
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [predefWord, setPredefWord] = useState("")
    const [wordIndex, setWordIndex] = useState(-1)
    const [showTranslationForm, setShowTranslationForm] = useState(false)
    const [translationText, setTranslationText] = useState("")
    const [translationSource, setTranslationSource] = useState("")
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + corpusText.title })

    function submitted(word) {
        setWordFormVisible(false)
        associateWord(router.query.id, word.id, wordIndex).then(() => {
            router.replace(router.asPath)
            if (word.gloss === "" || word.gloss === null) {
                router.push("/word/" + word.language + "/" + word.text)
            }
        })
    }

    function showWordForm(text, index) {
        setWordFormVisible(true)
        setPredefWord(trimPunctuation(text.toLowerCase()))
        setWordIndex(index)
    }

    function trimPunctuation(text) {
        while (",.!?:;".includes(text.slice(text.length-1))) {
            text = text.slice(0, text.length-1)
        }
        return text
    }

    function submitTranslation() {
        addTranslation(router.query.id, translationText, translationSource).then(() => {
            setShowTranslationForm(false)
            router.replace(router.asPath)
        })
    }

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${corpusText.language}`}>{corpusText.languageFullName}</Link> {'> '}
            <Link href={`/corpus/${corpusText.language}`}>Corpus</Link> {'>'} </small>
            {corpusText.title}</h2>
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
                    <WordForm key={predefWord} submitted={submitted}
                              language={corpusText.language} languageReadOnly={true}
                              initialText={predefWord} textReadOnly={true}
                              initialSource={corpusText.sourceEditableText}/>
                }
            </div>
        ))}
        <SourceRefs source={corpusText.source}/>
        {corpusText.translations.length > 0 && <>
            <h3>Translations</h3>
            {corpusText.translations.map(t =>
                <div>{t.text} <SourceRefs source={t.source}/></div>
            )}
        </>}
        {allowEdit() && <p><button onClick={() => setShowTranslationForm(!showTranslationForm)}>Add translation</button></p>}
        {showTranslationForm && <>
            <p/>
            <textarea rows="10" cols="50" value={translationText} onChange={e => setTranslationText(e.target.value)}/>
            <table><tbody>
            <tr>
                <td><label htmlFor="source">Source:</label></td>
                <td><input type="text" id="source" value={translationSource} onChange={(e) => setTranslationSource(e.target.value)}/></td>
            </tr>
            </tbody></table>
            <button onClick={() => submitTranslation()}>Submit</button>
        </>}
    </>
}
