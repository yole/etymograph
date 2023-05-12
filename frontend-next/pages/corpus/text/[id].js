import {useEffect, useState} from "react";
import WordForm from "@/components/WordForm";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEdit } from '@fortawesome/free-solid-svg-icons'
import {WordWithStress} from "@/components/WordWithStress";
import {fetchBackend, associateWord} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";

export async function getStaticProps(context) {
    return fetchBackend(`corpus/text/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`corpus`)
    const paths = props.loaderData.corpusTexts.map(corpusText => ({params: {id: corpusText.id.toString()}}))
    return {paths, fallback: false}
}

export function CorpusTextWordLink(params) {
    const w = params.word
    const corpusText = params.corpusText
    const showWordForm = params.showWordForm
    const [hovered, setHovered] = useState(false)

    if (w.wordText || w.gloss) {
        return <span onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
            <Link href={`/word/${corpusText.language}/${w.wordText ?? w.text}${w.wordId !== null ? "/" + w.wordId : ""}`}>
                <WordWithStress text={w.text} stressIndex={w.stressIndex} stressLength={w.stressLength}/>
            </Link>
            {hovered && <span className="iconWithMargin"><FontAwesomeIcon icon={faEdit} onClick={() => showWordForm(w.text, w.index)}/></span>}
        </span>
    }
    else {
        return <span className="undefWord" onClick={() => showWordForm(w.text, w.index)}>{w.text}</span>
    }
}

export default function CorpusText(params) {
    const corpusText = params.loaderData
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [predefWord, setPredefWord] = useState("")
    const [wordIndex, setWordIndex] = useState(-1)
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + corpusText.title })

    function submitted(word) {
        setWordFormVisible(false)
        associateWord(params.id, word.id, wordIndex).then(() => {
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
        while (",.!?:".includes(text.slice(text.length-1))) {
            text = text.slice(0, text.length-1)
        }
        return text
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
                    <WordForm submitted={submitted} language={corpusText.language} predefWord={predefWord} initialSource={corpusText.source}/>
                }
            </div>
        ))}
        {corpusText.source != null &&
            <div className="source">Source: {corpusText.source.startsWith("http") ? <a href={corpusText.source}>{corpusText.source}</a> : corpusText.source}</div>
        }
    </>
}
