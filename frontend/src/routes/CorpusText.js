import {useLoaderData, useNavigate, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useState} from "react";
import WordForm from "./WordForm";

export async function loader({params}) {
    return fetch(process.env.REACT_APP_BACKEND_URL + "corpus/text/" + params.id, { headers: { 'Accept': 'application/json'} })
}

export default function CorpusText() {
    const corpusText = useLoaderData()
    const [wordFormVisible, setWordFormVisible] = useState(false);
    const [predefWord, setPredefWord] = useState("")
    const revalidator = useRevalidator()
    const navigate = useNavigate()

    function submitted(word) {
        setWordFormVisible(false)
        revalidator.revalidate()
        if (word.gloss === "" || word.gloss === null) {
            navigate("/word/" + word.language + "/" + word.text)
        }
    }

    function showWordForm(text) {
        setWordFormVisible(true)
        setPredefWord(text.toLowerCase())
    }

    return <>
        <h2>{corpusText.title}</h2>
        {corpusText.lines.map(l => (
            <div>
                <table><tbody>
                    <tr>
                        {l.words.map(w => <td>
                            {w.wordText && <Link to={`/word/${corpusText.language}/${w.wordText}`}>{w.text}</Link>}
                            {!w.wordText && <span className="undefWord" onClick={() => showWordForm(w.text)}>{w.text}</span>}
                        </td>)}
                    </tr>
                    <tr>
                        {l.words.map(w => <td>{w.gloss}</td>)}
                    </tr>
                </tbody></table>
            </div>
        ))}
        {wordFormVisible && <WordForm submitted={submitted} language={corpusText.language} predefWord={predefWord}/>}
    </>
}
